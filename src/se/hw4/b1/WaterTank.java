package se.hw4.b1;

import java.util.ArrayList;
import java.util.List;

/**
 * 实验四 B1：恒温水箱（20℃ ~ 100℃）状态机。
 *
 * <p>状态集合：休眠 SLEEPING、监测 MONITORING、加热 HEATING、缺水告警 WATER_SHORTAGE、维修 REPAIRING。
 * <p>事件集合：POWER_ON、CLOCK_2300、CLOCK_0700、TEMPERATURE_CHANGED、WATER_STATUS_CHANGED、
 * TANK_DAMAGED、REPAIR_DONE。
 * <p>水温室温下限 LOW = 20℃，上限 HIGH = 100℃。
 */
public class WaterTank {

    /** 状态枚举，附带中文名便于日志输出。 */
    public enum State {
        SLEEPING("休眠"),
        MONITORING("监测"),
        HEATING("加热"),
        WATER_SHORTAGE("缺水告警"),
        REPAIRING("维修");

        private final String label;

        State(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** 触发状态迁移的事件。 */
    public enum Event {
        POWER_ON,
        CLOCK_2300,
        CLOCK_0700,
        TEMPERATURE_CHANGED,
        WATER_STATUS_CHANGED,
        TANK_DAMAGED,
        REPAIR_DONE
    }

    public static final double LOW_TEMPERATURE = 20.0;
    public static final double HIGH_TEMPERATURE = 100.0;

    private State state = State.SLEEPING;
    private double temperature = 25.0;
    private boolean hasWater = true;
    private boolean relayClosed = false;
    private final List<String> transitionLog = new ArrayList<>();

    public State state() {
        return state;
    }

    public double temperature() {
        return temperature;
    }

    public boolean hasWater() {
        return hasWater;
    }

    /** 继电器电源是否闭合（true 表示正在烧水）。 */
    public boolean relayClosed() {
        return relayClosed;
    }

    public List<String> transitionLog() {
        return transitionLog;
    }

    /** 温度传感器与水量传感器的采样值统一从这里注入，
     * 然后触发一次状态评估。 */
    public void sense(double temperature, boolean hasWater) {
        this.temperature = temperature;
        this.hasWater = hasWater;
        handle(Event.TEMPERATURE_CHANGED);
    }

    public void setWater(boolean hasWater) {
        this.hasWater = hasWater;
        handle(Event.WATER_STATUS_CHANGED);
    }

    /** 状态迁移的核心：根据当前状态与事件，
     * 在守卫条件成立时执行动作并切换状态。 */
    public void handle(Event event) {
        State from = state;

        // 复合状态「运行中」上的公共迁移：
        // 23:00 一律断开电源进入休眠，07:00 重新恢复运行
        if (event == Event.CLOCK_2300 && state != State.SLEEPING) {
            openRelay();
            state = State.SLEEPING;
        } else if (state == State.SLEEPING
                && (event == Event.CLOCK_0700 || event == Event.POWER_ON)) {
            state = State.MONITORING;
        } else {
            switch (state) {
                case MONITORING:
                    boolean sampled = event == Event.TEMPERATURE_CHANGED
                            || event == Event.WATER_STATUS_CHANGED;
                    if (sampled) {
                        if (temperature < LOW_TEMPERATURE) {
                            if (hasWater) {
                                // 低于 20℃ 且水箱有水：闭合继电器开始烧水
                                closeRelay();
                                state = State.HEATING;
                            } else {
                                // 低于 20℃ 但无水：绝不打开电源
                                state = State.WATER_SHORTAGE;
                            }
                        }
                    }
                    break;

                case WATER_SHORTAGE:
                    // 缺水保护：只有补水完成且温度仍低于 20℃
                    // 才允许开始烧水
                    boolean refilled = event == Event.WATER_STATUS_CHANGED
                            && hasWater;
                    if (refilled && temperature < LOW_TEMPERATURE) {
                        closeRelay();
                        state = State.HEATING;
                    }
                    break;

                case HEATING:
                    boolean boiled = event == Event.TEMPERATURE_CHANGED
                            && temperature >= HIGH_TEMPERATURE;
                    if (boiled) {
                        // 达到 100℃ 断开电源，回到恒温监测
                        openRelay();
                        state = State.MONITORING;
                    } else if (event == Event.TANK_DAMAGED) {
                        openRelay();
                        state = State.REPAIRING;
                    }
                    break;

                case REPAIRING:
                    if (event == Event.REPAIR_DONE) {
                        // 维修完成后复位到监测状态
                        state = State.MONITORING;
                    }
                    break;

                default:
                    break;
            }
        }
        if (from != state) {
            String detail = String.format("%.1f", temperature);
            transitionLog.add(from.label() + " --[" + event + "]--> "
                    + state.label() + "  (T=" + detail + "℃, 有水=" + hasWater
                    + ", 继电器=" + (relayClosed ? "闭合" : "断开") + ")");
        }
    }

    private void closeRelay() {
        relayClosed = true;
    }

    private void openRelay() {
        relayClosed = false;
    }

    /** 演示：完整走一遍一天中的典型场景。 */
    public static void main(String[] args) {
        WaterTank tank = new WaterTank();
        System.out.println("初始状态：" + tank.state().label());

        tank.handle(Event.POWER_ON);
        System.out.println("上电后：" + tank.state().label());

        tank.sense(18.0, true);
        System.out.println("水温 18℃ 且有水：" + tank.state().label() + "，继电器=" + tank.relayClosed());

        tank.sense(55.0, true);
        tank.sense(100.0, true);
        System.out.println("水温升到 100℃：" + tank.state().label() + "，继电器=" + tank.relayClosed());

        tank.sense(15.0, false);
        System.out.println("水温 15℃ 但无水：" + tank.state().label() + "，继电器=" + tank.relayClosed());

        tank.setWater(true);
        System.out.println("补水完成：" + tank.state().label() + "，继电器=" + tank.relayClosed());

        tank.handle(Event.CLOCK_2300);
        System.out.println("23:00 到达：" + tank.state().label() + "，继电器=" + tank.relayClosed());

        tank.handle(Event.CLOCK_0700);
        System.out.println("07:00 到达：" + tank.state().label());

        tank.sense(18.0, true);
        System.out.println("早上水温 18℃ 且有水：" + tank.state().label());

        tank.handle(Event.TANK_DAMAGED);
        System.out.println("加热过程中水箱烧坏：" + tank.state().label());
        tank.handle(Event.REPAIR_DONE);
        System.out.println("维修完成：" + tank.state().label());

        System.out.println("\n状态迁移轨迹：");
        for (String line : tank.transitionLog()) {
            System.out.println("  " + line);
        }
    }
}
