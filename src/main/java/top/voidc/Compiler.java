package top.voidc;

import org.antlr.v4.runtime.CharStreams;

import java.io.IOException;

public class Compiler {
    public static void main(String[] args) throws IOException {
        final var input = CharStreams.fromStream(System.in);
        final var lexer = new SysyLexer(input);
    }
}