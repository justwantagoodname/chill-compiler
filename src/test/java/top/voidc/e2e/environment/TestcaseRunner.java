package top.voidc.e2e.environment;

import java.io.IOException;
import java.util.List;

/**
 * 运行测试样例所的执行器接口
 * 可以返回boolean代表然后打印错误日志测试结果，也可以抛出异常
 */
public interface TestcaseRunner {
    /**
     * 获取执行器的名称
     * @return 执行器名称
     */
    String getName();

    /**
     * 运行汇编器的名称
     * @return 汇编器的名称
     */
    String getAssemblerName();


    /**
     * 获取编译 sysy 的源代码的命令行选项
     * @return 命令行选项
     */
    List<String> getCompileArgument(Testcase test);

    /**
     * 在运行所有测试样例前的准备工作，可以用来编译标准库
     */
    default boolean beforeAll() {
        return true;
    };

    /**
     * 运行每个测试样例前的准备工作
     * @param test 测试样例
     * @return 是否准备成功，失败时会终止该测试
     */
    default boolean prepareTest(Testcase test) {
        return true;
    };

    /**
     * 汇编并编译测试样例
     * @param test 测试样例
     * @return 汇编结果
     */
    boolean compileTest(Testcase test) throws IOException, InterruptedException;

    /**
     * 运行测试样例
     * @param test 测试样例
     * @return 是否运行成功，并且期望运行正确
     */
    boolean runTest(Testcase test) throws IOException, InterruptedException;

    /**
     * 清理结果
     * @param test
     * @return 清理结果
     */
    boolean cleanup(Testcase test) throws IOException, InterruptedException;
}
