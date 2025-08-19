# ❄️ Chill-Compiler
[![ARM Functional Test](https://github.com/justwantagoodname/chill-compiler/actions/workflows/maven.yml/badge.svg)](https://github.com/justwantagoodname/chill-compiler/actions/workflows/maven.yml)

[![希冀 Gitlab Mirror](https://github.com/justwantagoodname/chill-compiler/actions/workflows/mirror.yml/badge.svg)](https://github.com/justwantagoodname/chill-compiler/actions/workflows/mirror.yml)

## Intro

Chill (**C**hill **H**ierarchical **I**ntermediate **L**anguage **L**owerer) Compiler 是疾旋鼬编译器的重构版本，旨在提供一个更清晰、更易于维护的编译器架构。
此版编译器中间语言名为Ice IR，提供了健全（迫真）的 API 和合理的抽象层次，旨在简化编译器的开发和维护。

## Features
- 基于 Java 语言开发，尽最大努力为您提供 *Spring Boot®*-like 的编译器开发体验，支持模块化开发与依赖注入
- 和前代相比完全不同 IR 设计，提供更清晰的 API 和合理的抽象层次
- JNI 交互接口，用于方便的传递 IR 至 C/C++ 代码进行高复杂度优化（大饼）
- 更多**疾旋鼬**（Yay！）

## Roadmap

### Stage 1: Lexer & Parser
- [x] 使用 ANTLR 生成 Lexer、Parser
- [x] 分析得到等价的类 LLVM IR 表示 (Ice IR)

### Stage 2: LLVM
- [x] 使用 Ice IR 表示生成文本级别的 LLVM IR
- [x] 使用 LLVM API 或者 LLC 生成可执行文件进行测试

### Stage 3: Optimization
- [x] 进行 SSA 转换
- [x] 进行常量传播
- [x] 进行死代码消除
- [x] 公共表达式消除

### Stage 4: Code Generation
- [x] 指令选择
- [x] 寄存器分配
- [ ] 指令重排
- [x] 生成目标代码 (GNU ARM RISC-V汇编)

### Stage 5: Explore
- [ ] 探索前沿的优化技术

## Wiki
### Requirements
理论上您可以使用任何 Java™ Development Kit 发行版，版本为 24 或者更高版本，然而本项目仅在
 OpenJDK™ 24 上进行了测试并表现良好，不保证在其他 JDK 上的兼容性。

同时完成以下设置
- `-Xss8M`: 设置线程栈大小为 8MB，避免在处理特定文件时出现 StackOverflowError

### SSH 测试可用环境变量
- `SSH_HOST`: SSH 服务器地址 
- `SSH_PORT`: SSH 服务器端口
- `SSH_USERNAME`: SSH 用户名
- `SSH_PASSWORD`: SSH 密码
- `SSH_BASE_DIR`: SSH 服务器上的工作目录
- `SSH_PRIVATE_KEY`: SSH 私钥路径，当 `SSH_PASSWORD` 未设置时使能，或者使用默认值
- `TESTCASE_DIR`: 测试用例目录，多个目录用 `;` 分隔

### 可用 JVM 选项
- `chill.runner`: 指定使用的运行器类型，可选`SSHIRRunner`（运行 IR 测试，使用LLVM后端）和 `SSHARM64ASMRunner` （使用chill-compiler后端），默认为 `SSHIRRunner`
- `chill.ci`: 是否启用 CI 模式，默认为 `false`，启用后将禁用或者启用一些功能以适应 CI 环境，除非您知道自己在做什么，否则请默认为 `false`
- `chill.timeout`: 设置测试用例的超时时间，可以传入`timeout`指令支持的时间字符串如`5m`等，默认为 5 秒