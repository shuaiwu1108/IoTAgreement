import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;
import java.util.*;

/**
 * @description: walk方式获取数据
 **/
public class MultiThreadedWalkDemo {

    private static final Logger LOGGER = LogManager.getLogger(MultiThreadedWalkDemo.class);
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

    private Snmp snmp = null;

    public void  initSnmp() throws IOException {
        //1、初始化多线程消息转发类
        MessageDispatcher messageDispatcher = new MessageDispatcherImpl();
        //其中要增加三种处理模型。如果snmp初始化使用的是Snmp(TransportMapping<? extends Address> transportMapping) ,就不需要增加
        messageDispatcher.addMessageProcessingModel(new MPv1());
        messageDispatcher.addMessageProcessingModel(new MPv2c());
        //当要支持snmpV3版本时，需要配置user
        OctetString localEngineID = new OctetString(MPv3.createLocalEngineID());
        USM usm = new USM(SecurityProtocols.getInstance().addDefaultProtocols(), localEngineID, 0);

        OctetString userName1 = new OctetString(username);
        OctetString authPass = new OctetString(authPassword);
        OctetString privPass = new OctetString(privPassword);
        UsmUser user = new UsmUser(userName1, AuthMD5.ID, authPass, PrivAES128.ID, privPass);

        usm.addUser(user.getSecurityName(), user);
        messageDispatcher.addMessageProcessingModel(new MPv3(usm));
        //2、创建transportMapping  ip为本地ip，可以不设置
        //UdpAddress updAddr = (UdpAddress) GenericAddress.parse("udp:127.0.0.1/161");
        TransportMapping<?> transportMapping = new DefaultUdpTransportMapping();
        //3、正式创建snmp
        snmp = new Snmp(messageDispatcher, transportMapping);
        //开启监听
        snmp.listen();
    }

    private Target createTarget(String oid) {
        Target target = null;
        int version = 1;
        if (!(version == SnmpConstants.version3 || version == SnmpConstants.version2c || version == SnmpConstants.version1)) {
            //log.error("参数version异常");
            return target;
        }
        if (version == SnmpConstants.version3) {
            target = new UserTarget();
            //snmpV3需要设置安全级别和安全名称，其中安全名称是创建snmp指定user设置的new OctetString("SNMPV3")
            target.setSecurityLevel(SecurityLevel.AUTH_PRIV);
            target.setSecurityName(new OctetString(this.username));
        } else {
            //snmpV1和snmpV2需要指定团体名名称
            target = new CommunityTarget();
            ((CommunityTarget) target).setCommunity(new OctetString(this.username));
            if (version == SnmpConstants.version2c) {
                target.setSecurityModel(SecurityModel.SECURITY_MODEL_SNMPv2c);
            }
        }
        target.setVersion(version);
        //必须指定，没有设置就会报错。
        target.setAddress(GenericAddress.parse(this.addressGet));
        target.setRetries(3);
        target.setTimeout(2000);
        return target;
    }

    private static PDU createPDU(int version, int type, String oid) {
        PDU pdu = null;
        if (version == SnmpConstants.version3) {
            pdu = new ScopedPDU();
        } else {
            pdu = new PDUv1();
        }
        pdu.setType(type);
        //可以添加多个变量oid
        /*for(String oid:oids){
            pdu.add(new VariableBinding(new OID(oid)));
        }*/
        pdu.add(new VariableBinding(new OID(oid)));
        return pdu;
    }


    /**
     * WALK方式请求
     * @param oid
     */
    public void snmpWalk(String oid) {
        try {
            LOGGER.info("walk type");
            List<Map> list = new ArrayList<Map>();
            //1、初始化snmp,并开启监听
            initSnmp();
            //2、创建目标对象
            Target target = createTarget(oid);
            //3、创建报文
            PDU pdu = createPDU(1, PDU.GETNEXT, oid);
            //4、发送报文，并获取返回结果
            boolean matched = true;
            while (matched) {
                ResponseEvent responseEvent = snmp.send(pdu, target);
                if (responseEvent == null || responseEvent.getResponse() == null) {
                    System.out.println("TimeOut...");
                    break;
                }
                PDU response = responseEvent.getResponse();
                String nextOid = null;
                Vector<? extends VariableBinding> variableBindings = response.getVariableBindings();
                for (int i = 0; i < variableBindings.size(); i++) {
                    Map map = new HashMap();
                    VariableBinding variableBinding = variableBindings.elementAt(i);
                    Variable variable = variableBinding.getVariable();
                    nextOid = variableBinding.getOid().toDottedString();
                    //如果不是这个节点下的oid则终止遍历，否则会输出很多，直到整个遍历完。
                    if (!nextOid.startsWith(oid)) {
                        matched = false;
                        break;
                    }
                    map.put("oid", nextOid);
                    map.put("value",variable);
                    list.add(map);
                    System.out.println("oid:" + nextOid + ",value:"  + variable);
                    LOGGER.info("oid:" + nextOid + ",value:"  + variable);
                }
                if (!matched) {

                    break;
                }
                pdu.clear();
                pdu.add(new VariableBinding(new OID(nextOid)));
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    //开启监控的main方法。
    public static void main(String[] args) throws IOException {
        MultiThreadedWalkDemo multithreadedtrapreceiver = new MultiThreadedWalkDemo();
        multithreadedtrapreceiver.run();
    }

    public void run() {
        try {
            LOGGER.info("开始监听walk信息!");
            LOGGER.info("开始获取端口名称!");
            String healthOID1 = "1.3.6.1.2.1.2.2.1.2";
            this.snmpWalk(healthOID1);
            LOGGER.info("网口流量");
            String healthOID2 = "1.3.6.1.2.1.2.2.1.10";
            this.snmpWalk(healthOID2);
            LOGGER.info("网口状态");
            String healthOID3 = "1.3.6.1.2.1.2.2.1.16";
            this.snmpWalk(healthOID3);
            LOGGER.info("网口速率");
            String healthOID4 = "1.3.6.1.2.1.31.1.1.1.15";
            this.snmpWalk(healthOID4);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
