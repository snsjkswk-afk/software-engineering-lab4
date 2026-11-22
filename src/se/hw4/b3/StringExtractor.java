package se.hw4.b3;

import java.util.ArrayList;
import java.util.List;

/**
 * 实验四 B3：从源代码文件中提取全部字符串字面量的状态机。
 *
 * <p>字符串由一对双引号界定；字符串内的反斜杠会转义紧随其后的一个字符，
 * 转义对在输出时去掉反斜杠、保留被转义的字符。例如源码中
 * {@code "输出\"Hello World!\""} 应当提取出 {@code 输出"Hello World!"}。
 *
 * <p>三个状态恰好对应三种词法上下文：
 * <ul>
 *   <li>NORMAL —— 普通扫描，等待字符串的起始双引号；</li>
 *   <li>IN_STRING —— 字符串内部，逐字符累积内容，等结束双引号；</li>
 *   <li>ESCAPE —— 刚读到反斜杠，下一个字符必须无条件接收，
 *       不能作为定界符解释。</li>
 * </ul>
 */
public class StringExtractor {

    public enum State {
        NORMAL("普通扫描"),
        IN_STRING("字符串内"),
        ESCAPE("转义");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private State state = State.NORMAL;
    private final StringBuilder current = new StringBuilder();
    private final List<String> result = new ArrayList<>();
    private boolean terminated = true;

    /** 提取源码中的全部字符串；未闭合的字符串视为不完整，不计入结果。 */
    public List<String> extract(String source) {
        state = State.NORMAL;
        current.setLength(0);
        result.clear();
        terminated = true;

        for (int i = 0; i < source.length(); i++) {
            step(source.charAt(i));
        }
        // 结束时仍停在字符串内部或转义态，说明引号没有闭合，丢弃这段内容
        if (state != State.NORMAL) {
            terminated = false;
            current.setLength(0);
        }
        return new ArrayList<>(result);
    }

    /** 单步状态迁移：输入一个字符，可能输出一个字符串。 */
    private void step(char c) {
        switch (state) {
            case NORMAL:
                if (c == '"') {
                    current.setLength(0);
                    state = State.IN_STRING;
                }
                break;

            case IN_STRING:
                if (c == '\\') {
                    // 遇到反斜杠，进入转义态，反斜杠本身不写入内容
                    state = State.ESCAPE;
                } else if (c == '"') {
                    // 结束双引号：当前内容即为一个完整字符串
                    result.add(current.toString());
                    current.setLength(0);
                    state = State.NORMAL;
                } else {
                    current.append(c);
                }
                break;

            case ESCAPE:
                // 被转义的字符原样保留，包括 \" 与 \\ 两种情况
                current.append(c);
                state = State.IN_STRING;
                break;

            default:
                break;
        }
    }

    public State state() {
        return state;
    }

    /** 最近一次提取是否存在未闭合的字符串。 */
    public boolean terminated() {
        return terminated;
    }

    public static void main(String[] args) {
        StringExtractor extractor = new StringExtractor();

        String demo = "String msg = \"一般学习一门编程语言的时候，"
                + "基本上第一个程序是输出\\\"Hello World!\\\"\";";
        System.out.println("源代码：");
        System.out.println("  " + demo);
        List<String> list = extractor.extract(demo);
        System.out.println("提取结果：");
        for (String s : list) {
            System.out.println("  \"" + s + "\"");
        }
        System.out.println("共提取 " + list.size() + " 个字符串。");
    }
}
