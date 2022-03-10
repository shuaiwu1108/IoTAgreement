package com.shuaiwu;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;

public class MqttProducer {

    private MqttClient mqttClient;

    public MqttProducer(String SERVER_URI,String CLIENT_ID){
        try {
            MemoryPersistence persistence = new MemoryPersistence();
            mqttClient = new MqttClient(SERVER_URI, CLIENT_ID, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setUserName("admin");
            connOpts.setPassword("password".toCharArray());
            System.out.println("Connecting to broker: "+ SERVER_URI);
            mqttClient.connect(connOpts);
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }
    public void send(String topic, int qos, boolean retained, String payload) {
        if (mqttClient == null){
            return;
        }
        try {
            mqttClient.publish(topic, payload.getBytes(), qos, retained);
        } catch (MqttException e) {
            System.out.println(e.getMessage());
        }
    }
}
