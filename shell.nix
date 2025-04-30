{ pkgs ? import (fetchTarball https://github.com/nixos/nixpkgs/archive/nixos-unstable.tar.gz) {} }:

let
  # 允许unfree包
  allowUnfree = { allowUnfree = true; };
  # 支持不同平台
  system = pkgs.stdenv.hostPlatform.system;
  
in pkgs.mkShell {
  buildInputs = with pkgs; [
    # Java开发环境
    temurin-bin-24
    maven
    
    # 开发工具
    git
    
    # ANTLR4工具(如果需要在命令行使用)
    antlr4
    
    # 编译器工具链
    clang
    llvm
    gcc
    
    # AArch64 (ARMv8)交叉编译工具链和运行时
    pkgsCross.aarch64-multiplatform.buildPackages.gcc
    pkgsCross.aarch64-multiplatform.buildPackages.binutils
    pkgsCross.aarch64-multiplatform.glibc
    
    # RISC-V 64GC交叉编译工具链和运行时
    pkgsCross.riscv64.buildPackages.gcc
    pkgsCross.riscv64.buildPackages.binutils
    pkgsCross.riscv64.glibc
    
    # QEMU
    qemu
    
    # 系统库
    glibc
    glibc.dev
  ];

  # Shell环境变量
  shellHook = ''
    export MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
    
    # 创建临时目录
    export TMPDIR=/tmp
    mkdir -p $TMPDIR/nix-shell-$UID
    
    # 设置AArch64交叉编译环境
    # export PATH=${pkgs.pkgsCross.aarch64-multiplatform.buildPackages.gcc}/bin:$PATH
    # export PATH=${pkgs.pkgsCross.aarch64-multiplatform.buildPackages.binutils}/bin:$PATH
    
    # 设置RISC-V 64GC交叉编译环境
    # export PATH=${pkgs.pkgsCross.riscv64.buildPackages.gcc}/bin:$PATH
    # export PATH=${pkgs.pkgsCross.riscv64.buildPackages.binutils}/bin:$PATH
    
    # 设置QEMU
    # export PATH=${pkgs.qemu}/bin:$PATH
  '';
}
