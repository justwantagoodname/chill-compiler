# Chill

[![ARM Functional Test](https://github.com/nullnan/chill-compiler/actions/workflows/maven.yml/badge.svg)](https://github.com/nullnan/chill-compiler/actions/workflows/maven.yml)

## Roadmap

### Stage 1: Lexer & Parser
- [x] 使用 ANTLR 生成 Lexer、Parser
- [x] 分析得到等价的类 LLVM IR 表示 (Ice IR)
### Stage 2: LLVM
- [x] 使用 Ice IR 表示生成文本级别的 LLVM IR
- [x] 使用 LLVM API 或者 LLC 生成可执行文件进行测试
### Stage 3: Optimization
- [ ] 进行 SSA 转换
- [ ] 进行常量传播
- [ ] 进行死代码消除
- [ ] 寄存器分配
### Stage 4: Code Generation
- [ ] 生成目标代码 (GNU ARM RISC-V汇编)

### Stage 5: Explore
- [ ] 探索前沿的优化技术

## Note
### 使用 LLVM 编译源文件
```bash
clang -S -emit-llvm testcases/temp.c -o testcases/temp.ll
llc -march=arm -mattr=+v7,+vfp2 -float-abi=hard testcases/temp.ll -o testcases/temp.s
armv7l-unknown-linux-gnueabihf-gcc testcases/temp.s -o testcases/temp_arm_from_ll
qemu-arm testcases/temp_arm_from_ll
```