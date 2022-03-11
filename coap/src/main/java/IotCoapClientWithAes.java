import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.RandomUtils;
import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Option;
import org.eclipse.californium.core.coap.OptionNumberRegistry;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.elements.exception.ConnectorException;
import com.alibaba.fastjson.JSONObject;

/**
 * CoAP客户端连接阿里云物联网平台，基于eclipse californium开发。
 * 自主接入开发流程及参数填写，请参见：
 * https://help.aliyun.com/document_detail/57697.html [使用对称加密自主接入]
 */
public class IotCoapClientWithAes {
    // ===================需要用户填写的参数，开始===========================
    // 地域ID，当前仅支持华东2
    private static String regionId = "cn-shanghai";
    // 产品productKey
    private static String productKey = "****";
    // 设备名成deviceName
    private static String deviceName = "****";
    // 设备密钥deviceSecret
    private static String deviceSecret = "****";
    //发送的消息内容payload
    private static String payload = "hello coap!!";
    // ===================需要用户填写的参数，结束===========================

    // 定义加密方式 MAC算法可选以下多种算法 HmacMD5 HmacSHA1，需与signmethod一致。
    private static final String HMAC_ALGORITHM = "hmacsha1";

    // CoAP接入地址，对称加密端口号是5682。
    private static String serverURI = "coap://" + productKey + ".coap." + regionId + ".link.aliyuncs.com:5682";

    // 发送消息用的Topic。需要在控制台自定义Topic，设备操作权限需选择为“发布”。
    private static String updateTopic = "/" + productKey + "/" + deviceName + "/user/update";

    // token option
    private static final int COAP2_OPTION_TOKEN = 2088;
    // seq option
    private static final int COAP2_OPTION_SEQ = 2089;

    // 加密算法sha256
    private static final String SHA_256 = "SHA-256";

    private static final int DIGITAL_16 = 16;
    private static final int DIGITAL_48 = 48;

    // CoAP客户端
    private CoapClient coapClient = new CoapClient();

    // token 7天有效，失效后需要重新获取。
    private String token = null;
    private String random = null;
    @SuppressWarnings("unused")
    private long seqOffset = 0;

    /**
     * 初始化CoAP客户端
     *
     * @param productKey 产品key
     * @param deviceName 设备名称
     * @param deviceSecret 设备密钥
     */
    public void conenct(String productKey, String deviceName, String deviceSecret) {
        try {
            // 认证uri，/auth
            String uri = serverURI + "/auth";

            // 只支持POST方法
            Request request = new Request(Code.POST, Type.CON);

            // 设置option
            OptionSet optionSet = new OptionSet();
            optionSet.addOption(new Option(OptionNumberRegistry.CONTENT_FORMAT, MediaTypeRegistry.APPLICATION_JSON));
            optionSet.addOption(new Option(OptionNumberRegistry.ACCEPT, MediaTypeRegistry.APPLICATION_JSON));
            request.setOptions(optionSet);

            // 设置认证uri
            request.setURI(uri);

            // 设置认证请求payload
            request.setPayload(authBody(productKey, deviceName, deviceSecret));

            // 发送认证请求
            CoapResponse response = coapClient.advanced(request);
            System.out.println(Utils.prettyPrint(response));
            System.out.println();

            // 解析请求响应
            JSONObject json = JSONObject.parseObject(response.getResponseText());
            token = json.getString("token");
            random = json.getString("random");
            seqOffset = json.getLongValue("seqOffset");
        } catch (ConnectorException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 发送消息
     *
     * @param topic 发送消息的Topic
     * @param payload 消息内容
     */
    public void publish(String topic, byte[] payload) {
        try {
            // 消息发布uri，/topic/${topic}
            String uri = serverURI + "/topic" + topic;

            // AES加密seq，seq=RandomUtils.nextInt()
            String shaKey = encod(deviceSecret + "," + random);
            byte[] keys = Hex.decodeHex(shaKey.substring(DIGITAL_16, DIGITAL_48));
            byte[] seqBytes = encrypt(String.valueOf(RandomUtils.nextInt()).getBytes("UTF-8"), keys);

            // 只支持POST方法
            Request request = new Request(CoAP.Code.POST, CoAP.Type.CON);

            // 设置option
            OptionSet optionSet = new OptionSet();
            optionSet.addOption(new Option(OptionNumberRegistry.CONTENT_FORMAT, MediaTypeRegistry.APPLICATION_JSON));
            optionSet.addOption(new Option(OptionNumberRegistry.ACCEPT, MediaTypeRegistry.APPLICATION_JSON));
            optionSet.addOption(new Option(COAP2_OPTION_TOKEN, token));
            optionSet.addOption(new Option(COAP2_OPTION_SEQ, seqBytes));
            request.setOptions(optionSet);

            // 设置消息发布uri
            request.setURI(uri);

            // 设置消息payload
            request.setPayload(encrypt(payload, keys));

            // 发送消息
            CoapResponse response = coapClient.advanced(request);

            System.out.println("----------------");
            System.out.println(request.getPayload().length);
            System.out.println("----------------");
            System.out.println(Utils.prettyPrint(response));

            // 解析消息发送结果
            String result = null;
            if (response.getPayload() != null) {
                result = new String(decrypt(response.getPayload(), keys));
            }
            System.out.println("payload: " + result);
            System.out.println();
        } catch (ConnectorException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (DecoderException e) {
            e.printStackTrace();
        }
    }

    /**
     * 生成认证请求内容
     *
     * @param productKey 产品key
     * @param deviceName 设备名字
     * @param deviceSecret 设备密钥
     * @return 认证请求
     */
    private String authBody(String productKey, String deviceName, String deviceSecret) {

        // 构建认证请求
        JSONObject body = new JSONObject();
        body.put("productKey", productKey);
        body.put("deviceName", deviceName);
        body.put("clientId", productKey + "." + deviceName);
        body.put("timestamp", String.valueOf(System.currentTimeMillis()));
        body.put("signmethod", HMAC_ALGORITHM);
        body.put("seq", DIGITAL_16);
        body.put("sign", sign(body, deviceSecret));

        System.out.println("----- auth body -----");
        System.out.println(body.toJSONString());

        return body.toJSONString();
    }

    /**
     * 设备端签名
     *
     * @param params 签名参数
     * @param deviceSecret 设备密钥
     * @return 签名十六进制字符串
     */
    private String sign(JSONObject params, String deviceSecret) {

        // 请求参数按字典顺序排序
        Set<String> keys = getSortedKeys(params);

        // sign、signmethod、version、resources除外
        keys.remove("sign");
        keys.remove("signmethod");
        keys.remove("version");
        keys.remove("resources");

        // 组装签名明文
        StringBuffer content = new StringBuffer();
        for (String key : keys) {
            content.append(key);
            content.append(params.getString(key));
        }

        // 计算签名
        String sign = encrypt(content.toString(), deviceSecret);
        System.out.println("sign content=" + content);
        System.out.println("sign result=" + sign);

        return sign;
    }

    /**
     * 获取JSON对象排序后的key集合
     *
     * @param json 需要排序的JSON对象
     * @return 排序后的key集合
     */
    private Set<String> getSortedKeys(JSONObject json) {
        SortedMap<String, String> map = new TreeMap<String, String>();
        for (String key : json.keySet()) {
            String vlaue = json.getString(key);
            map.put(key, vlaue);
        }
        return map.keySet();
    }

    /**
     * 使用 HMAC_ALGORITHM 加密
     *
     * @param content 明文
     * @param secret 密钥
     * @return 密文
     */
    private String encrypt(String content, String secret) {
        try {
            byte[] text = content.getBytes("UTF-8");
            byte[] key = content.getBytes("UTF-8");
            SecretKeySpec secretKey = new SecretKeySpec(key, HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(secretKey.getAlgorithm());
            mac.init(secretKey);
            return Hex.encodeHexString(mac.doFinal(text));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * SHA-256
     *
     * @param str 待加密的报文
     */
    private String encod(String str) {
        MessageDigest messageDigest;
        String encdeStr = "";
        try {
            messageDigest = MessageDigest.getInstance(SHA_256);
            byte[] hash = messageDigest.digest(str.getBytes("UTF-8"));
            encdeStr = Hex.encodeHexString(hash);
        } catch (NoSuchAlgorithmException e) {
            System.out.println(String.format("Exception@encod: str=%s;", str));
            e.printStackTrace();
            return null;
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return encdeStr;
    }

    // AES 加解密算法
    private static final String IV = "543yhjy97ae7fyfg";
    private static final String TRANSFORM = "AES/CBC/PKCS5Padding";
    private static final String ALGORITHM = "AES";

    /**
     * key length = 16 bits
     */
    private byte[] encrypt(byte[] content, byte[] key) {
        return encrypt(content, key, IV);
    }

    /**
     * key length = 16 bits
     */
    private byte[] decrypt(byte[] content, byte[] key) {
        return decrypt(content, key, IV);
    }

    /**
     * aes 128 cbc key length = 16 bits
     */
    private byte[] encrypt(byte[] content, byte[] key, String ivContent) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            IvParameterSpec iv = new IvParameterSpec(ivContent.getBytes("UTF-8"));
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
            return cipher.doFinal(content);
        } catch (Exception ex) {
            System.out.println(
                    String.format("AES encrypt error, %s, %s, %s", content, Hex.encodeHex(key), ex.getMessage()));
            return null;
        }
    }

    /**
     * aes 128 cbc key length = 16 bits
     */
    private byte[] decrypt(byte[] content, byte[] key, String ivContent) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            IvParameterSpec iv = new IvParameterSpec(ivContent.getBytes("UTF-8"));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
            return cipher.doFinal(content);
        } catch (Exception ex) {
            System.out.println(String.format("AES decrypt error, %s, %s, %s", Hex.encodeHex(content),
                    Hex.encodeHex(key), ex.getMessage()));
            return null;
        }
    }

    public static void main(String[] args) throws InterruptedException, UnsupportedEncodingException {
        IotCoapClientWithAes client = new IotCoapClientWithAes();
        client.conenct(productKey, deviceName, deviceSecret);
        client.publish(updateTopic, payload.getBytes("UTF-8"));
    }
}
