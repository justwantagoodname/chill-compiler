{ pkgs ? import <nixpkgs> { } }:

let
  # 允许unfree包
  allowUnfree = { allowUnfree = true; };
  # 支持不同平台
  system = pkgs.stdenv.hostPlatform.system;
  
in pkgs.mkShell {
  buildInputs = with pkgs; [
    # Java开发环境
    jdk21
    jdk17
    maven
    
    # 开发工具
    git
    
    # ANTLR4工具(如果需要在命令行使用)
    antlr4
    
    # 编译器工具链
    clang
    llvm
    gcc
    
    # ARM交叉编译工具链和运行时
    pkgsCross.armv7l-hf-multiplatform.buildPackages.gcc
    pkgsCross.armv7l-hf-multiplatform.buildPackages.binutils
    pkgsCross.armv7l-hf-multiplatform.glibc
    
    # QEMU
    qemu
    
    # 系统库
    glibc
    glibc.dev
  ];

  # Shell环境变量
  shellHook = ''
    export JAVA_HOME=${pkgs.jdk17}/lib/openjdk
    export MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
    
    # 创建临时目录
    export TMPDIR=/tmp
    mkdir -p $TMPDIR/nix-shell-$UID
    
    # 设置交叉编译环境
    export PATH=${pkgs.pkgsCross.armv7l-hf-multiplatform.buildPackages.gcc}/bin:$PATH
    export PATH=${pkgs.pkgsCross.armv7l-hf-multiplatform.buildPackages.binutils}/bin:$PATH
    
    # 设置QEMU
    export PATH=${pkgs.qemu}/bin:$PATH
  '';
}
