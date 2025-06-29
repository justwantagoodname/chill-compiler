package top.voidc.ir;

import top.voidc.frontend.ir.IceBlockVisitor;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IceBlock extends IceUser implements List<IceInstruction> {
    private final List<IceInstruction> instructions;
    private final IceFunction function; // 所属函数

    public IceBlock(IceFunction parentFunction, String name) {
        super(name, IceType.VOID);
        this.function = parentFunction;
        this.instructions = new ArrayList<>();
    }

    public IceBlock(IceFunction parentFunction) {
        super("L" + parentFunction.generateLocalValueName(), IceType.VOID);
        this.function = parentFunction;
        this.instructions = new ArrayList<>();
    }

    public IceFunction getFunction() {
        return function;
    }

    /**
     * 移除当前基本块index指令*后*中所有的指令，前驱和后继关系由destroy中的相关代码维护
     * @param index 指令索引 index不会删除
     */
    private void removeAfterInstruction(int index) {
        final var destroyList = instructions.subList(index + 1, instructions.size());
        if (!destroyList.isEmpty()) {
            Log.w("在基本块中间插入了终止指令，确定这是想要的吗？");
        }
        final var iterator = destroyList.listIterator();
        while (iterator.hasNext()) {
            final var instruction = iterator.next();
            instruction.setParent(null);
            iterator.remove();
        }
    }

    /**
     * 在基本块最后插入指令，会自动维护前驱后继关系
     * @param instruction 指令
     */
    public void addInstruction(IceInstruction instruction) {
        instructions.add(instruction);
        if (instruction.isTerminal()) removeAfterInstruction(instructions.size() - 1);
    }

    /**
     * 在基本块最前面插入指令，会自动维护前驱后继关系
     * @param instruction 指令
     */
    public void addInstructionAtFront(IceInstruction instruction) {
        this.instructions.addFirst(instruction);
        if (instruction.isTerminal()) {
            Log.w("在基本块最前面插入了终止指令，确定这是想要的吗？");
            removeAfterInstruction(0);
        }
    }

    public void addInstructionAfter(IceInstruction instruction, IceInstruction after) {
        int index = instructions.indexOf(after);
        if (index == -1) {
            throw new IllegalArgumentException("指令不在基本块中");
        }
        instructions.add(index + 1, instruction);
        if (instruction.isTerminal()) removeAfterInstruction(index);
    }

    @Deprecated
    public List<IceInstruction> getInstructions() {
        return instructions;
    }

    @Deprecated
    public List<IceInstruction> instructions() {
        return instructions;
    }

    public List<IceBlock> successors() {
        return getSuccessors();
    }

    /**
     * 获取当前基本块的后继基本块
     * @implNote 后继基本块是指当前基本块的最后一条指令的操作数中是基本块的操作数对于ret指令和unreachable指令，后继基本块为空
     * @return 后继基本块列表
     */
    public List<IceBlock> getSuccessors() {
        if (instructions.isEmpty()) return List.of();
        // 取最后一条指令
        final var terminator = instructions.getLast();
        if (terminator.isTerminal()) {
            switch (terminator) {
                case IceBranchInstruction branch -> {
                    return branch.getOperands().stream()
                            .filter(iceUser -> iceUser instanceof IceBlock)
                            .map(iceUser -> (IceBlock) iceUser)
                            .collect(Collectors.toList());
                }
                case IceRetInstruction _, IceUnreachableInstruction _ -> {
                    return List.of();
                }
                default -> throw new IllegalStateException("Unexpected value: " + terminator);
            }
        } else {
            return List.of();
        }
    }

    public List<IceBlock> predecessors() {
        return getPredecessors();
    }

    public List<IceBlock> getPredecessors() {
        return getUsers().stream()
                .filter(iceUser -> iceUser instanceof IceInstruction)
                .map(inst -> ((IceInstruction) inst).getParent()).toList();
    }

    /**
     * 现在应该直接再最后的终结指令上添加后继基本块，现在添加的后继基本块不会有任何作用
     */
    @Deprecated
    public void addSuccessor(IceBlock block) {}

    /**
     * @see #addSuccessor(IceBlock)
     */
    @Deprecated
    public void removeSuccessor(IceBlock block) {
    }

    @Override
    public String getReferenceName(boolean withType) {
        return (withType ? "label " : "") + "%" + getName();
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append(this.getName()).append(":\n");
        instructions
                .forEach(instr -> {
                    builder.append("\t");
                    instr.getTextIR(builder);
                    builder.append("\n");
                });
    }

    public static IceBlock fromTextIR(String textIR, IceFunction parentFunction, Map<String, IceValue> environment) {
        return buildIRParser(textIR).basicBlock().accept(new IceBlockVisitor(parentFunction, environment));
    }

    public static IceBlock fromTextIR(String textIR, IceFunction parentFunction) {
        return buildIRParser(textIR).basicBlock().accept(new IceBlockVisitor(parentFunction, new HashMap<>()));
    }

    @Override
    public void destroy() {
        getUsers().forEach(iceUser -> {
            assert iceUser instanceof IceInstruction;
            switch (iceUser) {
                case IcePHINode phi -> phi.removeOperand(this);
                case IceBranchInstruction br -> {
                    final var userBlock = br.getParent();
                    if (userBlock == this) {
                        br.destroy();
                        return;
                    }

                    if (br.isConditional()) {
                        IceBranchInstruction jump;
                        if (br.getTrueBlock() == this) {
                            jump = new IceBranchInstruction(userBlock, br.getFalseBlock());
                        } else {
                            jump = new IceBranchInstruction(userBlock, br.getTrueBlock());
                        }
                        br.destroy();
                        userBlock.add(jump);
                    } else {
                        // 这里是无条件跳转的分支，直接替换unreachable指令
                        br.destroy();
                        userBlock.add(new IceUnreachableInstruction(userBlock));
                    }
                }
                default -> throw new IllegalStateException("Unexpected user: " + iceUser);
            }
        });
        this.clear();
        assert getUsers().isEmpty();
        super.destroy();
    }

    @Override
    public int size() {
        return instructions.size();
    }

    @Override
    public boolean isEmpty() {
        return instructions.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return instructions.contains(o);
    }

    private class IceInstructionListIterator implements ListIterator<IceInstruction> {
        private final ListIterator<IceInstruction> delegate;
        private IceInstruction lastReturned;
        
        private IceInstructionListIterator(int index) {
            this.delegate = instructions.listIterator(index);
        }
        
        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }
        
        @Override
        public IceInstruction next() {
            lastReturned = delegate.next();
            return lastReturned;
        }
        
        @Override
        public boolean hasPrevious() {
            return delegate.hasPrevious();
        }
        
        @Override
        public IceInstruction previous() {
            lastReturned = delegate.previous();
            return lastReturned;
        }
        
        @Override
        public int nextIndex() {
            return delegate.nextIndex();
        }
        
        @Override
        public int previousIndex() {
            return delegate.previousIndex();
        }
        
        @Override
        public void remove() {
            if (lastReturned == null) {
                throw new IllegalStateException();
            }
            lastReturned.setParent(null);
            lastReturned.destroy();
            lastReturned = null;
            delegate.remove();
        }
        
        @Override
        public void set(IceInstruction e) {
            delegate.set(e);
        }
        
        @Override
        public void add(IceInstruction e) {
            delegate.add(e);
            if (e.isTerminal()) {
                removeAfterInstruction(delegate.previousIndex());
            }
        }
    }

    @Override
    public Iterator<IceInstruction> iterator() {
        return listIterator();
    }

    @Override
    public Object[] toArray() {
        return instructions.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return instructions.toArray(a);
    }

    @Override
    public boolean add(IceInstruction iceInstruction) {
        addInstruction(iceInstruction);
        return true;
    }

    /**
     * 将当前基本块中的某个指令移动出当前基本块 指令并没有销毁！！！
     * @param o element to be removed from this list, if present
     * @return true if this list contained the specified element
     */
    @Override
    public boolean remove(Object o) {
        if (!(o instanceof IceInstruction instruction)) {
            return false;
        }
        if (!instructions.contains(instruction)) {
            return false;
        }
        instruction.setParent(null);
        instructions.remove(instruction);
        return true;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return new HashSet<>(instructions).containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends IceInstruction> c) {
        boolean modified = false;
        for (IceInstruction instruction : c) {
            add(instruction);
            modified = true;
        }
        return modified;
    }

    @Override
    public boolean addAll(int index, Collection<? extends IceInstruction> c) {
        ListIterator<IceInstruction> iterator = listIterator(index);
        for (IceInstruction instruction : c) {
            iterator.add(instruction);
        }
        return !c.isEmpty();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean modified = false;
        for (Object o : c) {
            if (remove(o)) {
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        Iterator<IceInstruction> it = iterator();
        boolean modified = false;
        while (it.hasNext()) {
            if (!c.contains(it.next())) {
                it.remove();
                modified = true;
            }
        }
        return modified;
    }

    @Override
    public void clear() {
        safeForEach(IceInstruction::destroy);
    }

    @Override
    public IceInstruction get(int index) {
        return instructions.get(index);
    }

    @Override
    public IceInstruction set(int index, IceInstruction element) {
        IceInstruction oldElement = instructions.get(index);
        instructions.set(index, element);
        if (element.isTerminal()) {
            removeAfterInstruction(index);
        }
        return oldElement;
    }

    @Override
    public void add(int index, IceInstruction element) {
        ListIterator<IceInstruction> iterator = listIterator(index);
        iterator.add(element);
    }

    @Override
    public IceInstruction remove(int index) {
        IceInstruction instruction = instructions.get(index);
        instruction.setParent(null);
        instruction.destroy();
        return instruction;
    }

    @Override
    public int indexOf(Object o) {
        return instructions.indexOf(o);
    }

    @Override
    public int lastIndexOf(Object o) {
        return instructions.lastIndexOf(o);
    }

    @Override
    public ListIterator<IceInstruction> listIterator() {
        return new IceInstructionListIterator(0);
    }

    @Override
    public ListIterator<IceInstruction> listIterator(int index) {
        return new IceInstructionListIterator(index);
    }

    @Override
    public List<IceInstruction> subList(int fromIndex, int toIndex) {
        return instructions.subList(fromIndex, toIndex);
    }

    @Override
    public void forEach(Consumer<? super IceInstruction> action) {
        instructions.forEach(action);
    }

    /**
     * 采用复制方案来对基本块中指令遍历，会轻微降低性能，仅在需要遍历同时删除时使用
     * @param action 对 instruction 执行的操作，可以删除
     */
    public void safeForEach(Consumer<? super IceInstruction> action) {
        List.copyOf(instructions).forEach(action);
    }

    @Override
    public Stream<IceInstruction> stream() {
        return instructions.stream();
    }
}
