package com.shuaiwu;

public class Test {
    public void testSend() {
        String serverURI="tcp://localhost:61613";
        String clientID="demo_mqtt";
        MqttProducer mqttProducer = new MqttProducer(serverURI, clientID);
        String msg ="";
        while (true){
            msg = "time:" + System.currentTimeMillis();
            mqttProducer.send("topic/msg02", 1, true, msg);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void testSub() {
        String serverURI="tcp://localhost:61613";
        String clientID="demo_mqtt000";
        MqttSubscriber mqttSubscriber = new MqttSubscriber(serverURI, clientID);
        mqttSubscriber.subscribe("topic/msg02");
    }
}
