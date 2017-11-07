package com.xxxtai.simulator.view;

import com.xxxtai.express.model.Car;
import com.xxxtai.express.view.DrawingGraph;
import com.xxxtai.simulator.controller.AGVCpuRunnable;
import com.xxxtai.simulator.model.AGVCar;
import com.xxxtai.express.constant.City;
import com.xxxtai.express.constant.Constant;
import com.xxxtai.express.constant.State;
import com.xxxtai.express.toolKit.Common;
import com.xxxtai.express.view.FileNameDialog;
import com.xxxtai.express.view.RoundButton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;


@Component
public class SchedulingGui extends JPanel {
    private static final long serialVersionUID = 1L;

    private RoundButton settingGuiBtn;

    private RoundButton drawingGuiBtn;

    private PrintWriter printWriter;

    private JLabel stateLabel;

    @Resource
    private DrawingGraph drawingGraph;

    private ArrayList<Car> AGVArray;

    private Car selectCar;

    private SchedulingGui() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        RoundButton schedulingGuiBtn = new RoundButton("调度界面");
        schedulingGuiBtn.setBounds(0, 0, screenSize.width / 3, screenSize.height / 20);
        schedulingGuiBtn.setForeground(new Color(30, 144, 255));
        schedulingGuiBtn.setBackground(Color.WHITE);

        settingGuiBtn = new RoundButton("设置界面");
        settingGuiBtn.setBounds(screenSize.width / 3, 0, screenSize.width / 3, screenSize.height / 20);

        drawingGuiBtn = new RoundButton("制图界面");
        drawingGuiBtn.setBounds(2 * screenSize.width / 3, 0, screenSize.width / 3, screenSize.height / 20);

        stateLabel = new JLabel();
        stateLabel.setBounds(0, 24 * screenSize.height / 26, screenSize.width, screenSize.height / 26);
        stateLabel.setFont(new Font("宋体", Font.BOLD, 20));
        stateLabel.setForeground(Color.RED);

        this.setLayout(null);
        this.add(schedulingGuiBtn);
        this.add(settingGuiBtn);
        this.add(drawingGuiBtn);
        this.add(stateLabel);

        this.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    for (Car car : AGVArray) {
                        if (Math.abs(e.getX() - car.getX()) < 40 && Math.abs(e.getY() - car.getY()) < 40) {
                            if (car.getState() == State.FORWARD || (car.getState() == State.BACKWARD)) {
                                car.setState(State.STOP);
                            } else if (car.getState() == State.STOP) {
                                car.setState(State.FORWARD);
                            }
                        }
                    }
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    for (Car car : AGVArray) {
                        if (Math.abs(e.getX() - car.getX()) < 40 && Math.abs(e.getY() - car.getY()) < 40) {
                            selectCar = car;
                        }
                    }

                    OptionView optionView = new OptionView("请选择");
                    optionView.setLocation(e.getX(), e.getY());
                    optionView.setOnDialogListener((option) -> {
                        optionView.dispose();
                        if (option.equals(OptionView.Option.SHIPMENT)) {
                            FileNameDialog fileNameDialog = new FileNameDialog("到达城市:");
                            fileNameDialog.setOnDialogListener((cityName, buttonState) -> {
                                fileNameDialog.dispose();
                                if (buttonState) {
                                    printWriter.println(Constant.QR_PREFIX + Integer.toHexString(selectCar.getAtEdge().cardNum) +
                                            Constant.SPLIT + Long.toHexString(City.valueOfName(cityName).getCode()) + Constant.SUFFIX);
                                    printWriter.flush();
                                    System.out.println("sysout" + cityName);
                                }
                            });
                        } else if (option.equals(OptionView.Option.UNLOADING)) {
                            ((AGVCpuRunnable)selectCar.getCommunicationRunnable()).sendStateToSystem(selectCar.getAGVNum(), State.UNLOADED.getValue());
                            ((AGVCar)selectCar).finishedDuty();
                        }
                    });
                }
            }
        });
        AGVArray = new ArrayList<>();
    }

    public void init(ApplicationContext context) {

        for (int i = 0; i < 4; i++) {
            AGVArray.add(context.getBean(AGVCar.class));
            AGVArray.get(i).init(i + 1);
        }
        new Timer(50, new RepaintTimerListener()).start();
        new Timer(100, new DetectCollideTimerListener()).start();

        try {
            Socket socket = new Socket("127.0.0.1", 8899);
            printWriter = new PrintWriter(socket.getOutputStream());
            printWriter.println(Constant.QR_PREFIX + 0 + Constant.SPLIT + 0 + Constant.SUFFIX);
            printWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        drawingGraph.drawingMap(g, DrawingGraph.Style.SIMULATOR);
        drawingGraph.drawingAGV(g, AGVArray, this);
    }

    class RepaintTimerListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            repaint();
            for (Car car : AGVArray) {
                if (car.getState().equals(State.FORWARD)) {
                    car.stepByStep();
                }
                car.heartBeat();
            }
        }
    }

    class DetectCollideTimerListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            for (int i = 0; i < AGVArray.size(); i++) {
                AGVCar car1 = (AGVCar) AGVArray.get(i);
                if (!car1.isOnDuty()) {
                    continue;
                }
                for (int j = i + 1; j < AGVArray.size(); j++) {
                    AGVCar car2 = (AGVCar) AGVArray.get(j);
                    if (!car2.isOnDuty()) {
                        continue;
                    }
                    if ((Math.abs(car1.getX() - car2.getX()) + Math.abs(car1.getY() - car2.getY())) < 60) {
                        car1.setState(State.COLLIED);
                        car2.setState(State.COLLIED);
                        stateLabel.setText(car1.getAGVNum() + "AGV 和 " + car2.getAGVNum() + "AGV相撞");
                    }
                }
            }
        }
    }

    public void getGuiInstance(JFrame simulatorMain, JPanel settingGui, JPanel drawingGui) {
        settingGuiBtn.addActionListener(e -> Common.changePanel(simulatorMain, settingGui));
        drawingGuiBtn.addActionListener(e -> Common.changePanel(simulatorMain, drawingGui));
    }
}
