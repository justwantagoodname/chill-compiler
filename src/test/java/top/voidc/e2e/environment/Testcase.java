package top.voidc.e2e.environment;

import java.io.File;

/**
 * Testcase 位于测试机本地，运行时被分发到 TestRunner 上执行
 * @param name 测试名应该保证在一次测试中不重复
 * @param in
 * @param out
 * @param src
 */
public record Testcase(String name, File in, File out, File src, File asm) {
    @Override
    public String toString() {
        return name;
    }
}