package top.voidc.optimizer.pass.unit;

import top.voidc.ir.IceUnit;
import top.voidc.misc.Log;
import top.voidc.optimizer.pass.CompilePass;

import java.io.*;
import java.util.Map;

public class Feeler implements CompilePass<IceUnit> {
    static public Map<String, IceUnit> detected;

    private String feelerName = "feeler";

    public void setFeelerName(String name){
        this.feelerName = name;
    }

    @Override
    public String getName() {
        return "Feeler";
    }

    @Override
    public boolean run(IceUnit target) {
        Log.d(target.getTextIR());

        IceUnit deepCopy = deepCopy(target);
        if (deepCopy != null) {
            detected.put(feelerName, deepCopy); // 添加键值对
        } else {
            // 处理拷贝失败的情况（可选）
            Log.e("Deep copy failed for target: " + target);
        }
        return false;
    }

    // 通过序列化实现深拷贝
    private IceUnit deepCopy(IceUnit original) {
        try {
            // 序列化对象
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(original);
            out.flush();

            // 反序列化对象
            ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
            ObjectInputStream in = new ObjectInputStream(bis);
            return (IceUnit) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}
