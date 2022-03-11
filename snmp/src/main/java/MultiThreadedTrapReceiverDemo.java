import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.snmp4j.*;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Vector;

public class MultiThreadedTrapReceiverDemo implements CommandResponder {


    private static final Logger logger = LogManager.getLogger(MultiThreadedTrapReceiverDemo.class);
    //用户名
    private String username = "nms2-admin";
    //鉴权密码
    private String authPassword = "hello123";
    //数据加密密码
    private String privPassword = "hello123";
    //trap地址
    private String address = "udp:127.0.0.1/162";
    //get 地址
    private String addressGet = "udp:127.0.0.1/161";

    private MultiThreadedMessageDispatcher dispatcher;
    private Snmp snmp = null;
    private Address listenAddress;
    private ThreadPool threadPool;
    public MultiThreadedTrapReceiverDemo() {


    }
    private void init() throws UnknownHostException, IOException {

        try {

            //创建接收SnmpTrap的线程池，参数： 线程名称及线程数
            threadPool = ThreadPool.create("Trap", 2);
            //创建一个多线程消息分发器，以同时处理传入的消息，该实例将用于分派传入和传出的消息
            dispatcher = new MultiThreadedMessageDispatcher(threadPool,
                    new MessageDispatcherImpl());
            //监听端的 ip地址 和 监听端口号
            listenAddress = GenericAddress.parse(address);
            //在指定的地址上创建UDP传输
            TransportMapping<?> transport;
            if (listenAddress instanceof UdpAddress) {

                //必须是本机地址
                transport = new DefaultUdpTransportMapping((UdpAddress) listenAddress);
            } else {

                transport = new DefaultTcpTransportMapping((TcpAddress) listenAddress);
            }
            //初始化snmp需要设置messageDispatcher里面的参数和TransportMapping参数
            snmp = new Snmp(dispatcher, transport);
            //消息分发器添加接收的版本信息
            /*      v1和v2都具有基本的读、写MIB功能。*
             *      v2增加了警报、批量数据获取、管理站和管理站通信能力。*
             *      v3在v2的基础上增加了USM，使用加密的数据和用户验证技术，提高了安全性*/
            snmp.getMessageDispatcher().addMessageProcessingModel(new MPv3());
            snmp.getMessageDispatcher().addMessageProcessingModel(new MPv2c());
            snmp.getMessageDispatcher().addMessageProcessingModel(new MPv1());
            //创建具有所提供安全协议支持的USM
            USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3 //根据本地IP地址和其他四个随机字节创建本地引擎ID
                    .createLocalEngineID()), 0);
            SecurityModels.getInstance().addSecurityModel(usm);
            // 添加安全协议,如果没有发过来的消息没有身份认证,可以跳过此段代码
            SecurityProtocols.getInstance().addDefaultProtocols();
            // 创建和添加用户
            OctetString userName1 = new OctetString(username);
            OctetString authPass = new OctetString(authPassword);
            OctetString privPass = new OctetString(privPassword);
            UsmUser usmUser1 = new UsmUser(userName1, AuthMD5.ID, authPass, PrivAES128.ID, privPass);
            //因为接受的Trap可能来自不同的主机，主机的Snmp v3加密认证密码都不一样，所以根据加密的名称，来添加认证信息UsmUser。
            //添加了加密认证信息的便可以接收来自发送端的信息。
            UsmUserEntry userEnty1 = new UsmUserEntry(userName1, usmUser1);
            UsmUserTable userTable = snmp.getUSM().getUserTable();
            // 添加其他用户
            userTable.addUser(userEnty1);
            //开启Snmp监听，可以接收来自Trap端的信息。
            snmp.listen();
            logger.info("开始监听trap告警");
        }catch (Exception e){

            e.printStackTrace();
            logger.info(e.getMessage());
        }
    }


    public void run() {

        try {

            init();
            snmp.addCommandResponder(this);
            System.out.println("开始监听Trap信息!");
        } catch (Exception ex) {

            ex.printStackTrace();
        }
    }

    //开启监控的main方法。
    public static void main(String[] args) throws IOException {

        MultiThreadedTrapReceiverDemo multithreadedtrapreceiver = new MultiThreadedTrapReceiverDemo();
        multithreadedtrapreceiver.run();
    }

    public void processPdu(CommandResponderEvent respEvnt) {
        // 解析Response
        logger.info("trap接受到告警消息，开始对消息进行处理");
        try {
            if (respEvnt != null && respEvnt.getPDU() != null) {
                for (int i = 0; i < respEvnt.getPDU().getVariableBindings().size(); i++) {
                    logger.info("消息体oid:"+respEvnt.getPDU().getVariableBindings().elementAt(i).getOid());
                    logger.info("消息体oid对应值:"+respEvnt.getPDU().getVariableBindings().elementAt(i).getVariable());
                }
                //获取对方交换机等设备的ip
                String ip = respEvnt.getPeerAddress().toString().split("/")[0].split(":")[0];
                logger.info("trap接受到告警消息，ip: " + ip);

                @SuppressWarnings("unchecked")
                Vector<VariableBinding> recVBs = (Vector<VariableBinding>) respEvnt.getPDU().getVariableBindings();
                for (int i = 0; i < recVBs.size(); i++) {
                    VariableBinding recVB = recVBs.elementAt(i);
                    String value = "oid: " + recVB.getOid() + ";oid对应值: " + recVB.getVariable();
                    logger.info(value);
                }
            }
        }catch (Exception e){

            e.printStackTrace();
        }
    }
}
