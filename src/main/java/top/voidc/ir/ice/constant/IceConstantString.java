package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Tool;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class IceConstantString extends IceConstantArray {
    // 此 value 时经过转义处理的IR文本格式的字符串
    private final String value;
    // 此 value 时为原始的字符串数组
    private final List<Byte> rawBytes;

    public static List<DataArrayElement> createStringElement(List<Byte> bytes) {
        // convert value to byte ArrayList in UTF-8 encoding
        List<DataArrayElement> elements = new ArrayList<>();
        for (byte b : bytes) {
            elements.add(new DataArrayElement(new IceConstantByte(b), 1));
        }
        return elements;
    }

    private IceConstantString(List<Byte> codepoints, String escapedString) {
        super(new IceArrayType(IceType.I8, codepoints.size()), createStringElement(codepoints));
        this.rawBytes = codepoints;
        this.value = escapedString;
    }

    /**
     * byd java，灵活构造器不让随便用折中一下
     * 创建常量字符串，会对输入value进行转义处理
     *
     * @param value 对应的 String 字面值
     */
    public static IceConstantString buildString(String value) {
        final var escapedCharSeq = setIRFormattedString(value);
        return new IceConstantString(escapedCharSeq.codepoints(), escapedCharSeq.escapedString());
    }

    @Override
    public IceConstantData castTo(IceType type) {
        return switch (type.getTypeEnum()) {
            case STRING -> this.clone();
            case ARRAY -> (IceConstantArray) this.clone();
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return IceConstantString.buildString(value);
    }

    public String getValue() {
        return value;
    }

    private record EscapedCharSeq(List<Byte> codepoints, String escapedString) {}

    /**
     * 同时附加一个 \\0 结尾
     * 将 value 中的：
     *   1) 反斜杠转义（如 "\\n"）解析为对应字符；
     *   2) 控制字符转换为 "\\XX"；
     *   3) 非 ASCII 字符按 UTF-8 分解为 "\\XX\\YY..."。
     */
    private static EscapedCharSeq setIRFormattedString(String value) {
        // 1) 添加一个真实的 '\0' 结束符
        String withTerminator = value + '\0';

        // 2) 先把所有 "\\n", "\\t" 等解析成实际字符
        StringBuilder parsed = new StringBuilder();
        for (int i = 0; i < withTerminator.length(); i++) {
            char c = withTerminator.charAt(i);
            if (c == '\\' && i + 1 < withTerminator.length()) {
                char d = withTerminator.charAt(++i);
                switch (d) {
                    case 'n':  parsed.append('\n');  break;
                    case 'r':  parsed.append('\r');  break;
                    case 't':  parsed.append('\t');  break;
                    case 'b':  parsed.append('\b');  break;
                    case 'f':  parsed.append('\f');  break;
                    case '\\': parsed.append('\\');  break;
                    case '\'': parsed.append('\'');  break;
                    case '\"': parsed.append('\"');  break;
                    default:   parsed.append(d);     break;
                }
            } else {
                parsed.append(c);
            }
        }

        // 3) 转成 UTF-8 字节流
        byte[] bytes = parsed.toString().getBytes(StandardCharsets.UTF_8);

        // 4) 构造输出和字节列表
        StringBuilder out = new StringBuilder();
        List<Byte> byteList = new ArrayList<>(bytes.length);
        for (byte b : bytes) {
            int ub = b & 0xFF;
            byteList.add(b);
            if (ub <= 0x1F || ub == 0x7F) {
                // 控制字符
                out.append('\\').append(String.format("%02X", ub));
            }
            else if (ub >= 0x20 && ub <= 0x7E) {
                // 可打印 ASCII
                out.append((char) ub);
            }
            else {
                // 非 ASCII（UTF-8 多字节序列中的字节）
                out.append('\\').append(String.format("%02X", ub));
            }
        }

        return new EscapedCharSeq(byteList, out.toString());
    }

    @Override
    public String getReferenceName(boolean withType) {
        return (withType ? getType() + " " : "") + "c\"" + value + "\"";
    }

    public List<Byte> getRawByte() {
        return rawBytes;
    }
}
