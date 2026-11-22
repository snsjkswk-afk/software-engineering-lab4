package se.hw4.b2;

import java.util.ArrayList;
import java.util.List;

/**
 * 实验四 B2：SafeHome 控制面板 ControlPanel 状态机。
 *
 * <p>状态集合：读取键入字符 READ_INPUT、系统功能选择 FUNCTION_SELECTION、锁定 LOCKED。
 * <p>密码固定为 4 位；连续输错 3 次即锁定；锁定时长 120 秒，超时后允许重新输入。
 */
public class ControlPanel {

    public enum State {
        READ_INPUT("读取键入字符"),
        FUNCTION_SELECTION("系统功能选择"),
        LOCKED("锁定");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public static final int PASSWORD_LENGTH = 4;
    public static final int MAX_WRONG_ATTEMPTS = 3;
    public static final long LOCK_DURATION_MILLIS = 120_000L;

    /** 简化时钟，便于在单元测试中直接推进虚拟时间。 */
    public static class SimClock {
        private long millis;

        public long now() {
            return millis;
        }

        public void advance(long deltaMillis) {
            millis += deltaMillis;
        }
    }

    private final String password;
    private final SimClock clock;

    private State state = State.READ_INPUT;
    private final StringBuilder buffer = new StringBuilder();
    private int wrongAttempts = 0;
    private long lockStartMillis = 0;
    private int lockedEventCount = 0;
    private final List<String> transitionLog = new ArrayList<>();

    public ControlPanel(String password) {
        this(password, new SimClock());
    }

    public ControlPanel(String password, SimClock clock) {
        if (password == null || password.length() != PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "密码必须是 " + PASSWORD_LENGTH + " 位");
        }
        this.password = password;
        this.clock = clock;
    }

    public State state() {
        return state;
    }

    public int wrongAttempts() {
        return wrongAttempts;
    }

    public int lockedEventCount() {
        return lockedEventCount;
    }

    public List<String> transitionLog() {
        return transitionLog;
    }

    /** 户主敲击一个按键，触发一次状态评估。 */
    public void keyPressed(char c) {
        switch (state) {
            case READ_INPUT:
                // 处于读取键入字符状态：把字符追加进缓冲区，
                // 凑满 4 位后立即比对
                buffer.append(c);
                if (buffer.length() == PASSWORD_LENGTH) {
                    verify();
                }
                break;
            case LOCKED:
                // 锁定期间键入一律丢弃
                lockedEventCount++;
                break;
            default:
                // 系统功能选择状态下按键交给功能处理，不影响状态机
                break;
        }
    }

    /** 决策节点：校验缓冲区中的 4 位密码。 */
    private void verify() {
        String input = buffer.toString();
        buffer.setLength(0);
        if (password.equals(input)) {
            wrongAttempts = 0;
            changeState(State.FUNCTION_SELECTION, "密码正确");
        } else {
            wrongAttempts++;
            if (wrongAttempts >= MAX_WRONG_ATTEMPTS) {
                lockStartMillis = clock.now();
                changeState(State.LOCKED,
                        "连续输错 " + wrongAttempts + " 次");
            } else {
                changeState(State.READ_INPUT,
                        "密码错误，错误次数=" + wrongAttempts + "，清空缓冲");
            }
        }
    }

    /** 定时器事件：锁定超过 120 秒后解除锁定，并清零错误次数。 */
    public void tick() {
        boolean timeout =
                clock.now() - lockStartMillis >= LOCK_DURATION_MILLIS;
        if (state == State.LOCKED && timeout) {
            wrongAttempts = 0;
            changeState(State.READ_INPUT, "锁定满 120 秒，解除锁定");
        }
    }

    /** 在系统功能选择状态下取消操作，回到读取键入字符状态。 */
    public void cancel() {
        if (state == State.FUNCTION_SELECTION) {
            changeState(State.READ_INPUT, "取消功能选择");
        }
    }

    private void changeState(State target, String reason) {
        State from = state;
        state = target;
        transitionLog.add(from.label() + " --[" + reason + "]--> "
                + target.label());
    }

    private void type(String keys) {
        for (char c : keys.toCharArray()) {
            keyPressed(c);
            tick();
        }
    }

    public static void main(String[] args) {
        ControlPanel panel = new ControlPanel("1234");
        System.out.println("初始状态：" + panel.state().label());

        panel.type("1234");
        System.out.println("输入正确密码 1234：" + panel.state().label());

        panel.cancel();
        System.out.println("取消功能选择：" + panel.state().label());

        panel.type("0000");
        System.out.println("第 1 次输错：" + panel.state().label() + "，错误次数=" + panel.wrongAttempts());
        panel.type("0000");
        System.out.println("第 2 次输错：" + panel.state().label() + "，错误次数=" + panel.wrongAttempts());
        panel.type("0000");
        System.out.println("第 3 次输错：" + panel.state().label() + "，错误次数=" + panel.wrongAttempts());

        panel.type("1234");
        System.out.println("锁定期间输入正确密码：" + panel.state().label()
                + "，被丢弃的按键数=" + panel.lockedEventCount());

        panel.tick();
        System.out.println("未满 120 秒：" + panel.state().label());

        panel.clockAdvance(120_000L);
        panel.tick();
        System.out.println("满 120 秒后：" + panel.state().label() + "，错误次数=" + panel.wrongAttempts());

        panel.type("1234");
        System.out.println("再次输入正确密码：" + panel.state().label());

        System.out.println("\n状态迁移轨迹：");
        for (String line : panel.transitionLog()) {
            System.out.println("  " + line);
        }
    }

    void clockAdvance(long millis) {
        clock.advance(millis);
    }
}
