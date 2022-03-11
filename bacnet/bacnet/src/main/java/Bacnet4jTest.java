import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;
import com.serotonin.bacnet4j.service.acknowledgement.AcknowledgementService;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyRequest;
import com.serotonin.bacnet4j.service.confirmed.WritePropertyRequest;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.enumerated.*;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.Real;

public class Bacnet4jTest {
    public void write() {
        LocalDevice device = null;
        try {
            device = localDeviceConnect("设备端ip");
            RemoteDevice rd = device.getRemoteDevice(2);
            WritePropertyRequest writePropertyRequest = new WritePropertyRequest(
                    new ObjectIdentifier(ObjectType.analogInput, 1),
                    PropertyIdentifier.presentValue,
                    null, new Real(77), null);
            device.send(rd, writePropertyRequest);
        } catch (Exception e) {
            e.printStackTrace();
            if (device != null) {
                device.terminate();
            }
        }

    }

    public void read() {
        LocalDevice device = null;
        try {
            device = localDeviceConnect("设备端ip");
            //获取远程设备连接
            RemoteDevice rd = device.getRemoteDevice(2);
            //组装请求
            ReadPropertyRequest readPropertyRequest = new ReadPropertyRequest(
                    new ObjectIdentifier(ObjectType.analogInput, 1),
                    PropertyIdentifier.objectIdentifier);
            //设备发送请求
            AcknowledgementService ack = device.send(rd, readPropertyRequest);
            System.out.println(ack.toString());
        } catch (Exception e) {
            e.printStackTrace();
            if (device != null) {
                device.terminate();
            }
        }
    }


    public LocalDevice localDeviceConnect(String broadcast) {
        LocalDevice device = null;
        try {
            //创建网络对象
            IpNetwork ipNetwork = new IpNetwork(broadcast, 255);
            //创建虚拟的本地设备
            device = new LocalDevice(12, new Transport(ipNetwork));
            //初始化
            device.initialize();
        } catch (Exception e) {
            e.printStackTrace();
            if (device != null) {
                device.terminate();
            }
        }
        return device;
    }
}
