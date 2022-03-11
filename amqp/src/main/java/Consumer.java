import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Consumer {
    private final static String QUEUE_NAME = "my_queue"; //队列名称
    private final static String EXCHANGE_NAME = "my_exchange"; //要使用的exchange的名称
    private final static String EXCHANGE_TYPE = "topic"; //要使用的exchange的名称
    private final static String EXCHANGE_ROUTING_KEY = "my_routing_key.#"; //exchange使用的routing-key

    public static void receive() throws IOException, TimeoutException {
        //创建连接工厂
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost("127.0.0.1"); //设置rabbitmq-server的地址
        connectionFactory.setPort(5672);  //使用的端口号
        connectionFactory.setVirtualHost("/");  //使用的虚拟主机

        //由连接工厂创建连接
        Connection connection = connectionFactory.newConnection();

        //通过连接创建信道
        Channel channel = connection.createChannel();

        //通过信道声明一个exchange，若已存在则直接使用，不存在会自动创建
        //参数：name、type、是否支持持久化、此交换机没有绑定一个queue时是否自动删除、是否只在rabbitmq内部使用此交换机、此交换机的其它参数（map）
        channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE, true, false, false, null);

        //通过信道声明一个queue，如果此队列已存在，则直接使用；如果不存在，会自动创建
        //参数：name、是否支持持久化、是否是排它的、是否支持自动删除、其他参数（map）
        channel.queueDeclare(QUEUE_NAME, true, false, false, null);

        //将queue绑定至某个exchange。一个exchange可以绑定多个queue
        channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, EXCHANGE_ROUTING_KEY);

        //创建消费者，指定要使用的channel。QueueingConsume类已经弃用，使用DefaultConsumer代替
        DefaultConsumer consumer = new DefaultConsumer(channel){
            //监听的queue中有消息进来时，会自动调用此方法来处理消息。但此方法默认是空的，需要重写
            @Override
            public void handleDelivery(java.lang.String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                java.lang.String msg = new java.lang.String(body);
                System.out.println("received msg: " + msg);
            }
        };

        //监听指定的queue。会一直监听。
        //参数：要监听的queue、是否自动确认消息、使用的Consumer
        channel.basicConsume(QUEUE_NAME, true, consumer);
    }

    public static void main(String[] args) {
        try {
            Consumer.receive();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TimeoutException e) {
            e.printStackTrace();
        }
    }
}
