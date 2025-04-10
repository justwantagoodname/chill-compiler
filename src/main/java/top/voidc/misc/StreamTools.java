package top.voidc.misc;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.*;

/**
 * StreamTools 是一个流操作工具类，提供类似 Python itertools 的常用函数。
 */
public class StreamTools {

    /**
     * 将两个 Stream 按照元素顺序合并成一个 Stream，类似 Python 的 zip 函数。
     * 如果两个流长度不同，将以较短的长度为准。
     *
     * @param s1     第一个流
     * @param s2     第二个流
     * @param zipper 合并两个元素的函数
     * @param <T1>   第一个流中的元素类型
     * @param <T2>   第二个流中的元素类型
     * @param <R>    返回的合并后流的元素类型
     * @return 合并后的 Stream
     */
    public static <T1, T2, R> Stream<R> zip(Stream<T1> s1, Stream<T2> s2, BiFunction<T1, T2, R> zipper) {
        List<T1> l1 = s1.collect(Collectors.toList());
        List<T2> l2 = s2.collect(Collectors.toList());
        int size = Math.min(l1.size(), l2.size());

        return IntStream.range(0, size)
                .mapToObj(i -> zipper.apply(l1.get(i), l2.get(i)));
    }

    /**
     * 对两个列表执行笛卡尔积组合，并使用提供的函数合并每一对元素。
     *
     * @param list1    第一个列表
     * @param list2    第二个列表
     * @param combiner 合并两个元素的函数
     * @param <T1>     第一个列表中的元素类型
     * @param <T2>     第二个列表中的元素类型
     * @param <R>      合并后的元素类型
     * @return 返回一个笛卡尔积流
     */
    public static <T1, T2, R> Stream<R> product(List<T1> list1, List<T2> list2, BiFunction<T1, T2, R> combiner) {
        return list1.stream()
                .flatMap(a -> list2.stream().map(b -> combiner.apply(a, b)));
    }

    /**
     * 将流中相邻的元素组成一对，类似 Python 的 itertools.pairwise。
     * 例如：[1, 2, 3] => (1,2), (2,3)
     *
     * @param stream 输入流
     * @param <T>    流中元素类型
     * @return 返回一个包含相邻元素对的流，使用 SimpleEntry 表示
     */
    public static <T> Stream<AbstractMap.SimpleEntry<T, T>> pairwise(Stream<T> stream) {
        List<T> list = stream.collect(Collectors.toList());
        return IntStream.range(0, list.size() - 1)
                .mapToObj(i -> new AbstractMap.SimpleEntry<>(list.get(i), list.get(i + 1)));
    }

    /**
     * 返回一个重复某个值的有限 Stream。
     *
     * @param value 要重复的值
     * @param count 重复次数
     * @param <T>   值的类型
     * @return 重复值的 Stream
     */
    public static <T> Stream<T> repeat(T value, int count) {
        return Stream.generate(() -> value).limit(count);
    }

    /**
     * 返回一个重复整个集合的 Stream，重复次数由 repeatCount 指定。
     *
     * @param values       要重复的集合
     * @param repeatCount  重复次数
     * @param <T>          集合中元素的类型
     * @return 重复后的 Stream
     */
    public static <T> Stream<T> cycle(Collection<T> values, int repeatCount) {
        return IntStream.range(0, repeatCount)
                .boxed()
                .flatMap(i -> values.stream());
    }

    /**
     * 类似 Python 的 itertools.islice，从流中截取指定范围的元素。
     * 支持起始位置、结束位置、步长（step）。
     *
     * @param stream 输入流
     * @param start  起始索引（包含）
     * @param end    结束索引（不包含）
     * @param step   步长（大于等于 1）
     * @param <T>    流中元素类型
     * @return 一个新的 Stream，仅包含选定范围内的元素
     */
    public static <T> Stream<T> islice(Stream<T> stream, int start, int end, int step) {
        if (start < 0 || end < start || step <= 0) {
            throw new IllegalArgumentException("参数无效：start >= 0, end >= start, step >= 1");
        }

        List<T> list = stream.collect(Collectors.toList());
        return IntStream.range(start, Math.min(end, list.size()))
                .filter(i -> (i - start) % step == 0)
                .mapToObj(list::get);
    }

}
