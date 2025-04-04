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
  ];

  # Shell环境变量
  shellHook = ''
    export JAVA_HOME=${pkgs.jdk17}/lib/openjdk
    export MAVEN_OPTS="-XX:+TieredCompilation -XX:TieredStopAtLevel=1"
  '';
}
