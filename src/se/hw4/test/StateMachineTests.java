package se.hw4.test;

import java.util.Arrays;
import java.util.List;

import se.hw4.b1.WaterTank;
import se.hw4.b2.ControlPanel;
import se.hw4.b3.StringExtractor;

/**
 * 实验四 回归测试用例集合。
 *
 * <p>不依赖任何第三方测试框架，直接以 {@code javac} 编译、{@code java} 运行，
 * 便于在无 Maven 仓库的环境中复现测试结果。
 */
public class StateMachineTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== B1 恒温水箱状态机 ===");
        testB1PowerOnEntersMonitoring();
        testB1LowTemperatureWithWaterStartsHeating();
        testB1LowTemperatureWithoutWaterDoesNotHeat();
        testB1Reach100DegreesStopsHeating();
        testB1NightSleepAndMorningWakeUp();
        testB1RepairFlow();

        System.out.println("\n=== B2 控制面板状态机 ===");
        testB2CorrectPassword();
        testB2WrongPasswordKeepsReading();
        testB2ThreeWrongAttemptsLocks();
        testB2LockReleasesAfter120Seconds();
        testB2KeysIgnoredWhileLocked();

        System.out.println("\n=== B3 字符串提取状态机 ===");
        testB3AssignmentExample();
        testB3MultipleStrings();
        testB3EmptyString();
        testB3EscapedBackslash();
        testB3NoString();
        testB3UnterminatedStringIsDiscarded();

        System.out.println("\n---------------------------------------------");
        System.out.println("通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------- B1

    private static void testB1PowerOnEntersMonitoring() {
        WaterTank tank = new WaterTank();
        check("B1-01 初始处于休眠", tank.state(), WaterTank.State.SLEEPING);
        tank.handle(WaterTank.Event.POWER_ON);
        check("B1-02 上电进入监测", tank.state(), WaterTank.State.MONITORING);
        check("B1-03 继电器断开", tank.relayClosed(), false);
    }

    private static void testB1LowTemperatureWithWaterStartsHeating() {
        WaterTank tank = startedTank();
        tank.sense(19.9, true);
        check("B1-04 低于 20℃ 有水开始加热", tank.state(), WaterTank.State.HEATING);
        check("B1-05 继电器闭合", tank.relayClosed(), true);
    }

    private static void testB1LowTemperatureWithoutWaterDoesNotHeat() {
        WaterTank tank = startedTank();
        tank.sense(15.0, false);
        check("B1-06 低于 20℃ 无水进入缺水告警", tank.state(), WaterTank.State.WATER_SHORTAGE);
        check("B1-07 无水绝不闭合继电器", tank.relayClosed(), false);
        tank.setWater(true);
        check("B1-08 补水后开始加热", tank.state(), WaterTank.State.HEATING);
        check("B1-09 补水后继电器闭合", tank.relayClosed(), true);
    }

    private static void testB1Reach100DegreesStopsHeating() {
        WaterTank tank = startedTank();
        tank.sense(20.0, true);
        check("B1-10 恰好 20℃ 不加热", tank.state(), WaterTank.State.MONITORING);
        tank.sense(19.0, true);
        tank.sense(99.9, true);
        check("B1-11 未到 100℃ 继续加热", tank.state(), WaterTank.State.HEATING);
        tank.sense(100.0, true);
        check("B1-12 达到 100℃ 断开电源", tank.state(), WaterTank.State.MONITORING);
        check("B1-13 达到 100℃ 后继电器断开", tank.relayClosed(), false);
    }

    private static void testB1NightSleepAndMorningWakeUp() {
        WaterTank tank = startedTank();
        tank.sense(19.0, true);
        tank.handle(WaterTank.Event.CLOCK_2300);
        check("B1-14 23:00 进入休眠", tank.state(), WaterTank.State.SLEEPING);
        check("B1-15 休眠时继电器必须断开", tank.relayClosed(), false);
        tank.handle(WaterTank.Event.CLOCK_0700);
        check("B1-16 07:00 恢复恒温烧水", tank.state(), WaterTank.State.MONITORING);
    }

    private static void testB1RepairFlow() {
        WaterTank tank = startedTank();
        tank.sense(19.0, true);
        tank.handle(WaterTank.Event.TANK_DAMAGED);
        check("B1-17 烧坏进入维修", tank.state(), WaterTank.State.REPAIRING);
        check("B1-18 维修时断电", tank.relayClosed(), false);
        tank.handle(WaterTank.Event.REPAIR_DONE);
        check("B1-19 维修完成复位到监测", tank.state(), WaterTank.State.MONITORING);
    }

    private static WaterTank startedTank() {
        WaterTank tank = new WaterTank();
        tank.handle(WaterTank.Event.POWER_ON);
        return tank;
    }

    // ---------------------------------------------------------------- B2

    private static void testB2CorrectPassword() {
        ControlPanel panel = new ControlPanel("1234");
        type(panel, "1234");
        check("B2-01 密码正确进入功能选择", panel.state(), ControlPanel.State.FUNCTION_SELECTION);
        check("B2-02 正确后错误次数归零", panel.wrongAttempts(), 0);
    }

    private static void testB2WrongPasswordKeepsReading() {
        ControlPanel panel = new ControlPanel("1234");
        type(panel, "0000");
        check("B2-03 输错 1 次仍处于读取状态", panel.state(), ControlPanel.State.READ_INPUT);
        check("B2-04 错误次数为 1", panel.wrongAttempts(), 1);
    }

    private static void testB2ThreeWrongAttemptsLocks() {
        ControlPanel panel = new ControlPanel("1234");
        type(panel, "0000");
        type(panel, "0000");
        check("B2-05 输错 2 次不锁定", panel.state(), ControlPanel.State.READ_INPUT);
        type(panel, "0000");
        check("B2-06 输错 3 次锁定", panel.state(), ControlPanel.State.LOCKED);
    }

    private static void testB2LockReleasesAfter120Seconds() {
        ControlPanel.SimClock clock = new ControlPanel.SimClock();
        ControlPanel panel = new ControlPanel("1234", clock);
        type(panel, "0000");
        type(panel, "0000");
        type(panel, "0000");
        check("B2-07 已锁定", panel.state(), ControlPanel.State.LOCKED);

        clock.advance(119_000L);
        panel.tick();
        check("B2-08 119 秒仍锁定", panel.state(), ControlPanel.State.LOCKED);

        clock.advance(1_000L);
        panel.tick();
        check("B2-09 满 120 秒解除锁定", panel.state(), ControlPanel.State.READ_INPUT);
        check("B2-10 解锁后错误次数归零", panel.wrongAttempts(), 0);

        type(panel, "1234");
        check("B2-11 解锁后可正常登录", panel.state(), ControlPanel.State.FUNCTION_SELECTION);
    }

    private static void testB2KeysIgnoredWhileLocked() {
        ControlPanel panel = new ControlPanel("1234");
        type(panel, "0000");
        type(panel, "0000");
        type(panel, "0000");
        type(panel, "1234");
        check("B2-12 锁定期间正确密码无效", panel.state(), ControlPanel.State.LOCKED);
        check("B2-13 锁定期间按键被丢弃", panel.lockedEventCount(), 4);
    }

    private static void type(ControlPanel panel, String keys) {
        for (char c : keys.toCharArray()) {
            panel.keyPressed(c);
            panel.tick();
        }
    }

    // ---------------------------------------------------------------- B3

    private static void testB3AssignmentExample() {
        StringExtractor extractor = new StringExtractor();
        String source = "String msg = \"一般学习一门编程语言的时候，基本上第一个程序是输出\\\"Hello World!\\\"\";";
        List<String> actual = extractor.extract(source);
        expect("B3-01 题目示例", actual,
                Arrays.asList("一般学习一门编程语言的时候，基本上第一个程序是输出\"Hello World!\""));
    }

    private static void testB3MultipleStrings() {
        StringExtractor extractor = new StringExtractor();
        List<String> actual = extractor.extract("String s = \"abc\" + \"def\";");
        expect("B3-02 一行多个字符串", actual, Arrays.asList("abc", "def"));
    }

    private static void testB3EmptyString() {
        StringExtractor extractor = new StringExtractor();
        List<String> actual = extractor.extract("String s = \"\";");
        expect("B3-03 空字符串", actual, Arrays.asList(""));
    }

    private static void testB3EscapedBackslash() {
        StringExtractor extractor = new StringExtractor();
        List<String> actual = extractor.extract("String path = \"C:\\\\temp\\\\a.txt\";");
        expect("B3-04 转义反斜杠", actual, Arrays.asList("C:\\temp\\a.txt"));
    }

    private static void testB3NoString() {
        StringExtractor extractor = new StringExtractor();
        List<String> actual = extractor.extract("int x = 1; // no string here");
        expect("B3-05 无字符串返回空列表", actual, Arrays.asList());
    }

    private static void testB3UnterminatedStringIsDiscarded() {
        StringExtractor extractor = new StringExtractor();
        List<String> actual = extractor.extract("String s = \"abc\nint y = 2;");
        expect("B3-06 未闭合字符串被丢弃", actual, Arrays.asList());
        check("B3-07 未闭合标记为 false", extractor.terminated(), false);
    }

    // ---------------------------------------------------------------- 断言

    private static void check(String name, Object actual, Object expected) {
        boolean ok = (actual == null) ? expected == null : actual.equals(expected);
        report(name, ok, String.valueOf(expected), String.valueOf(actual));
    }

    private static void expect(String name, List<String> actual, List<String> expected) {
        boolean ok = actual.equals(expected);
        report(name, ok, expected.toString(), actual.toString());
    }

    private static void report(String name, boolean ok, String expected, String actual) {
        if (ok) {
            passed++;
            System.out.println("  [PASS] " + name);
        } else {
            failed++;
            System.out.println("  [FAIL] " + name + "  期望=" + expected + "  实际=" + actual);
        }
    }
}
