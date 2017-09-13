package com.xxxtai.simulator.model;

import com.xxxtai.express.constant.Command;
import com.xxxtai.express.controller.CommunicationWithAGV;
import com.xxxtai.express.controller.TrafficControl;
import com.xxxtai.express.model.Car;
import com.xxxtai.express.model.Edge;
import com.xxxtai.express.model.Graph;
import com.xxxtai.simulator.controller.AGVCpuRunnable;
import com.xxxtai.express.constant.Constant;
import com.xxxtai.express.constant.Orientation;
import com.xxxtai.express.constant.State;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j(topic = "develop")
public class AGVCar implements Car{
    private @Getter
    int AGVNum;
    private Orientation orientation = Orientation.RIGHT;
    private ExecutorService executor;
    private Point position;
    private boolean finishEdge;
    private @Getter
    State state = State.STOP;
    private @Getter
    Edge atEdge;
    private boolean isFirstInquire = true;
    private int detectCardNum;
    private int lastDetectCardNum;
    private Map<Integer, Integer> cardCommandMap;
    private int stopCardNum;
    private long lastCommunicationTime;
    private long count_3s;
    @Resource
    private AGVCpuRunnable cpuRunnable;
    @Resource
    private Graph graph;

    public AGVCar() {
        this.executor = Executors.newSingleThreadExecutor();
        this.position = new Point(-100, -100);
        this.lastCommunicationTime = System.currentTimeMillis();
    }


    public void init(int AGVNum) {
        this.AGVNum = AGVNum;
        this.cardCommandMap = new HashMap<>();
        this.cpuRunnable.setCarModelToCpu(this);
        if (this.cpuRunnable.connect()) {
            this.executor.execute(this.cpuRunnable);
            this.cpuRunnable.heartBeat(AGVNum);
        }

        setAtEdge(graph.getEdgeMap().get(113 + (AGVNum - 1)*2));
    }

    @Override
    public void setReceiveCardNum(int i) {

    }

    private void setAtEdge(Edge edge) {
        this.atEdge = edge;
        this.position.x = this.atEdge.startNode.x;
        this.position.y = this.atEdge.startNode.y;
        this.finishEdge = false;
        judgeOrientation();
    }

    public void stepByStep() {
        if (!finishEdge && (state == State.FORWARD || state == State.BACKWARD)
                && this.atEdge != null) {
            int FORWARD_PIx = 3;
            if (this.atEdge.startNode.x == this.atEdge.endNode.x) {
                if (this.atEdge.startNode.y < this.atEdge.endNode.y) {
                    if (this.position.y < this.atEdge.endNode.y) {
                        this.position.y += FORWARD_PIx;
                    } else {
                        this.finishEdge = true;
                    }
                } else if (atEdge.startNode.y > atEdge.endNode.y) {
                    if (this.position.y > this.atEdge.endNode.y) {
                        this.position.y -= FORWARD_PIx;
                    } else {
                        this.finishEdge = true;
                    }
                }
            } else if (this.atEdge.startNode.y == this.atEdge.endNode.y) {
                if (this.atEdge.startNode.x < this.atEdge.endNode.x) {
                    if (this.position.x < this.atEdge.endNode.x) {
                        this.position.x += FORWARD_PIx;
                    } else {
                        this.finishEdge = true;
                    }
                } else if (this.atEdge.startNode.x > this.atEdge.endNode.x) {
                    if (this.position.x > this.atEdge.endNode.x) {
                        this.position.x -= FORWARD_PIx;
                    } else {
                        this.finishEdge = true;
                    }
                }
            }
        }

        int cardNum = detectRFIDCard();
        if (cardNum != 0 && cardNum != this.detectCardNum) {
            this.lastDetectCardNum = this.detectCardNum;
            this.detectCardNum = cardNum;
            log.info(this.AGVNum + "AGV detectRFIDCard:" + cardNum);
            if (cardNum == this.stopCardNum) {
                this.state = State.STOP;
                this.cpuRunnable.sendStateToSystem(this.AGVNum, 2);
            }
            this.cpuRunnable.sendReadCardToSystem(this.AGVNum, cardNum);
        }

        if (this.finishEdge && this.isFirstInquire && this.cardCommandMap.get(this.lastDetectCardNum) != null) {
            if (!swerve(this.cardCommandMap.get(this.lastDetectCardNum))) {
                this.state = State.STOP;
            } else {
                this.cardCommandMap.remove(this.lastDetectCardNum);
            }
        }
    }

    public void heartBeat() {
        if (this.count_3s == 60) {
            this.count_3s = 0;
            this.cpuRunnable.heartBeat(this.AGVNum);
        } else {
            this.count_3s++;
        }
    }

    private void judgeOrientation() {
        if (atEdge.startNode.x == atEdge.endNode.x) {
            if (atEdge.startNode.y < atEdge.endNode.y) {
                orientation = Orientation.DOWN;
            } else {
                orientation = Orientation.UP;
            }
        } else if (atEdge.startNode.y == atEdge.endNode.y) {
            if (atEdge.startNode.x < atEdge.endNode.x) {
                orientation = Orientation.RIGHT;
            } else {
                orientation = Orientation.LEFT;
            }
        }
    }

    private boolean patrolLine(Orientation orientation) {
        boolean isFound = false;
        for (Edge e : graph.getEdgeArray()) {
            if (this.atEdge.endNode.cardNum.equals(e.startNode.cardNum) && !this.atEdge.startNode.cardNum.equals(e.endNode.cardNum)) {
                if ((orientation == Orientation.RIGHT && e.startNode.y == e.endNode.y && e.startNode.x < e.endNode.x)
                        || (orientation == Orientation.DOWN && e.startNode.x == e.endNode.x && e.startNode.y < e.endNode.y)
                        || (orientation == Orientation.LEFT && e.startNode.y == e.endNode.y && e.startNode.x > e.endNode.x)
                        || (orientation == Orientation.UP && e.startNode.x == e.endNode.x && e.startNode.y > e.endNode.y)) {
                    setAtEdge(e);
                    isFound = true;
                    break;
                }
            } else if (this.atEdge.endNode.cardNum.equals(e.endNode.cardNum) && !this.atEdge.startNode.cardNum.equals(e.startNode.cardNum)) {
                if ((orientation == Orientation.RIGHT && e.startNode.y == e.endNode.y && e.startNode.x > e.endNode.x)
                        || (orientation == Orientation.DOWN && e.startNode.x == e.endNode.x && e.startNode.y > e.endNode.y)
                        || (orientation == Orientation.LEFT && e.startNode.y == e.endNode.y && e.startNode.x < e.endNode.x)
                        || (orientation == Orientation.UP && e.startNode.x == e.endNode.x && e.startNode.y < e.endNode.y)) {
                    setAtEdge(new Edge(e.endNode, e.startNode, e.realDistance, e.cardNum));
                    isFound = true;
                    break;
                }
            }
        }
        return isFound;
    }

    private boolean swerve(Integer commandValue) {//1、左转；2、右转；3、前进
        boolean isFound = false;
        this.isFirstInquire = false;
        if (commandValue == Command.TURN_LEFT.getValue()) {
            switch (this.orientation) {
                case RIGHT:
                    isFound = patrolLine(Orientation.UP);
                    break;
                case LEFT:
                    isFound = patrolLine(Orientation.DOWN);
                    break;
                case UP:
                    isFound = patrolLine(Orientation.LEFT);
                    break;
                case DOWN:
                    isFound = patrolLine(Orientation.RIGHT);
                    break;
            }
        } else if (commandValue == Command.TURN_RIGHT.getValue()) {
            switch (this.orientation) {
                case RIGHT:
                    isFound = patrolLine(Orientation.DOWN);
                    break;
                case LEFT:
                    isFound = patrolLine(Orientation.UP);
                    break;
                case UP:
                    isFound = patrolLine(Orientation.RIGHT);
                    break;
                case DOWN:
                    isFound = patrolLine(Orientation.LEFT);
                    break;
            }
        } else if (commandValue == Command.GO_AHEAD.getValue()) {
            isFound = patrolLine(this.orientation);
        }
        if (isFound) {
            this.isFirstInquire = true;
            this.state = State.FORWARD;
        }
        return isFound;
    }

    private int detectRFIDCard() {
        int foundCard = 0;
        if (Math.abs(this.position.x - this.atEdge.CARD_POSITION.x) < 4 && Math.abs(this.position.y - this.atEdge.CARD_POSITION.y) < 4)
            foundCard = this.atEdge.cardNum;

        if (Math.abs(this.position.x - this.atEdge.startNode.x) < 4 && Math.abs(this.position.y - this.atEdge.startNode.y) < 4)
            foundCard = this.atEdge.startNode.cardNum;

        if (Math.abs(this.position.x - this.atEdge.endNode.x) < 4 && Math.abs(this.position.y - this.atEdge.endNode.y) < 4)
            foundCard = this.atEdge.endNode.cardNum;

        return foundCard;
    }

    public void setCardCommandMap(String commandString) {
        String[] commandArray = commandString.split(Constant.SPLIT);
        stopCardNum = Integer.parseInt(commandArray[commandArray.length - 1], 16);
        for (int i = 0; i < commandArray.length - 1; i++) {
            String[] c = commandArray[i].split(Constant.SUB_SPLIT);
            this.cardCommandMap.put(Integer.parseInt(c[0],16), Integer.parseInt(c[1],16));
        }
        this.state = State.FORWARD;
    }

    public void changeState() {
        if (this.state == State.FORWARD || this.state == State.BACKWARD) {
            this.cpuRunnable.sendStateToSystem(AGVNum, State.STOP.getValue());
            this.state = State.STOP;
        } else if (this.state == State.STOP) {
            this.state = State.FORWARD;
            this.cpuRunnable.sendStateToSystem(AGVNum, State.FORWARD.getValue());
        }
    }

    public void stopTheAGV() {
        this.state = State.STOP;
        this.cpuRunnable.sendStateToSystem(AGVNum, State.STOP.getValue());
    }

    public void startTheAGV() {
        this.state = State.FORWARD;
        this.cpuRunnable.sendStateToSystem(AGVNum, State.FORWARD.getValue());
    }

    public Orientation getOrientation() {
        return this.orientation;
    }

    public long getLastCommunicationTime() {
        return this.lastCommunicationTime;
    }

    public void setLastCommunicationTime(long time) {
        this.lastCommunicationTime = time;
    }

    @Override
    public Runnable getCommunicationRunnable() {
        return cpuRunnable;
    }

    public void setNewCpuRunnable() {
        this.cpuRunnable = new AGVCpuRunnable();
        this.cpuRunnable.setCarModelToCpu(this);
        if (this.cpuRunnable.connect()) {
            this.executor.execute(this.cpuRunnable);
            this.cpuRunnable.sendStateToSystem(AGVNum, 2);
        }
    }

    @Override
    public int getX(){
        return position.x;
    }

    @Override
    public int getY(){
        return position.y;
    }

    @Override
    public TrafficControl getTrafficControl() {
        return null;
    }

    @Override
    public int getReadCardNum() {
        return 0;
    }

    @Override
    public boolean isOnDuty() {
        return false;
    }

    @Override
    public boolean isOnEntrance() {
        return false;
    }

    @Override
    public void setCommunicationWithAGV(CommunicationWithAGV communicationWithAGV) {}

    @Override
    public void sendMessageToAGV(String s) {}

    @Override
    public void setState(int i) {}

    @Override
    public void setRouteNodeNumArray(List<Integer> list) {}
}