package com.taoke.miquaner.serv.impl;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import com.mysql.jdbc.StringUtils;
import com.taobao.api.internal.toplink.embedded.websocket.util.StringUtil;
import com.taoke.miquaner.MiquanerApplication;
import com.taoke.miquaner.data.EConfig;
import com.taoke.miquaner.data.ESmsCode;
import com.taoke.miquaner.data.EToken;
import com.taoke.miquaner.data.EUser;
import com.taoke.miquaner.repo.ConfigRepo;
import com.taoke.miquaner.repo.SmsCodeRepo;
import com.taoke.miquaner.repo.TokenRepo;
import com.taoke.miquaner.repo.UserRepo;
import com.taoke.miquaner.serv.IUserServ;
import com.taoke.miquaner.util.BeanUtil;
import com.taoke.miquaner.util.DateUtils;
import com.taoke.miquaner.util.ErrorR;
import com.taoke.miquaner.util.Result;
import com.taoke.miquaner.view.*;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserServImpl implements IUserServ {

    private static final Logger logger = LogManager.getLogger(UserServImpl.class);
    private static final String NO_USER_FOUND = "没有该用户";
    private static final String USER_WRONG_PWD = "用户密码错误";
    private static final String ALREADY_REGISTERED_USER = "已经注册过，请直接登录，或者找回密码";
    private static final String NO_CORRECT_PHONE = "手机号格式不正确";
    private static final String NO_VERIFY_CODE = "请先获取验证码";
    private static final String VERIFY_CODE_EXPIRED = "验证码已过期，请重新获取验证码";
    private static final String WRONG_VERIFY_CODE = "验证码错误，请输入正确的验证码";
    private static final String NEED_NAME_UNIQUE = "该名字已被注册";
    private static final String NO_INV_CODE_FOUND = "没有找到该邀请码";
    private static final String THIRD_CAN_NOT_ENROLL = "您没有申请成为合伙人的权限";

    private static IAcsClient acsClient;

    static {
        //设置超时时间-可自行调整
        System.setProperty("sun.net.client.defaultConnectTimeout", "10000");
        System.setProperty("sun.net.client.defaultReadTimeout", "10000");
        //初始化ascClient需要的几个参数
        final String product = "Dysmsapi";//短信API产品名称（短信产品名固定，无需修改）
        final String domain = "dysmsapi.aliyuncs.com";//短信API产品域名（接口地址固定，无需修改）
        //替换成你的AK
        final String accessKeyId = "LTAIu1xsEil8OIsS";//你的accessKeyId,参考本文档步骤2
        final String accessKeySecret = "OYebPIYVSpyERmNOY2VkB34nU4rkdT";//你的accessKeySecret，参考本文档步骤2
        //初始化ascClient,暂时不支持多region（请勿修改）
        IClientProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
        try {
            DefaultProfile.addEndpoint("cn-hangzhou", "cn-hangzhou", product, domain);
        } catch (ClientException e) {
            logger.error("sms init failed.");
        }
        acsClient = new DefaultAcsClient(profile);
    }

    private Function<EUser, List<String>> eUserRFunction = user -> Arrays.asList(
            "" + user.getId(),
            user.getName(),
            user.getRealName(),
            user.getPhone(),
            user.getAliPayId(),
            user.getQqId(),
            user.getWeChatId(),
            user.getAnnouncement(),
            user.getAliPid(),
            user.getCode(),
            user.getExt()
    );

    private UserRepo userRepo;
    private TokenRepo tokenRepo;
    private SmsCodeRepo smsCodeRepo;
    private ConfigRepo configRepo;

    @Autowired
    public UserServImpl(UserRepo userRepo, TokenRepo tokenRepo, SmsCodeRepo smsCodeRepo, ConfigRepo configRepo) {
        this.userRepo = userRepo;
        this.tokenRepo = tokenRepo;
        this.smsCodeRepo = smsCodeRepo;
        this.configRepo = configRepo;
    }

    @Override
    public Object login(EUser user) {
        EUser one = this.userRepo.findByPhoneEquals(user.getPhone());
        if (null == one) {
            return Result.fail(new ErrorR(ErrorR.NO_USER_FOUND, NO_USER_FOUND));
        }

        if (!one.getPwd().equals(user.getPwd())) {
            return Result.fail(new ErrorR(ErrorR.USER_WRONG_PWD, USER_WRONG_PWD));
        }

        EToken token = this.tokenRepo.findByUser_Id(one.getId());
        if (null == token) {
            token = new EToken();
            token.setUser(one);
        }
        Date now = new Date();
        token.setToken(StringUtil.toMD5HexString(MiquanerApplication.DEFAULT_DATE_FORMAT.format(now)));
        token.setExpired(DateUtils.add(now, Calendar.DAY_OF_YEAR, 3));
        EToken eToken = this.tokenRepo.save(token);

        return tokenWithUser(eToken, one);
    }

    @Override
    public Object register(UserRegisterSubmit userRegisterSubmit) {
        Object x = checkSmsCode(userRegisterSubmit.getUser().getPhone(), userRegisterSubmit.getCode());
        if (x != null) return x;

        EUser byPhoneEquals = this.userRepo.findByPhoneEquals(userRegisterSubmit.getUser().getPhone());
        if (null != byPhoneEquals) {
            return Result.fail(new ErrorR(ErrorR.ALREADY_REGISTERED_USER, ALREADY_REGISTERED_USER));
        }

        EUser byCodeEquals = null;
        if (!StringUtils.isNullOrEmpty(userRegisterSubmit.getInvitation()) && !this.setSuperDivider(userRegisterSubmit)) {
            byCodeEquals = this.userRepo.findByCodeEquals(userRegisterSubmit.getInvitation());
            if (null == byCodeEquals) {
                return Result.fail(new ErrorR(ErrorR.NO_INV_CODE_FOUND, NO_INV_CODE_FOUND));
            }
            userRegisterSubmit.getUser().setpUser(byCodeEquals);
        }

        boolean try2ok = false;
        if (StringUtils.isNullOrEmpty(userRegisterSubmit.getUser().getName())) {
            try2ok = true;
            userRegisterSubmit.getUser().setName("觅" + ("" + Math.random()).substring(2, 10));
        }
        EUser byNameEquals = this.userRepo.findByNameEquals(userRegisterSubmit.getUser().getName());
        while (try2ok) {
            if (null == byNameEquals) {
                break;
            }
            userRegisterSubmit.getUser().setName("觅" + ("" + Math.random()).substring(2, 10));
            byNameEquals = this.userRepo.findByNameEquals(userRegisterSubmit.getUser().getName());
        }
        if (null != byNameEquals) {
            return Result.fail(new ErrorR(ErrorR.NEED_NAME_UNIQUE, NEED_NAME_UNIQUE));
        }

        EUser saved = this.userRepo.save(userRegisterSubmit.getUser());

        EToken token = this.tokenRepo.findByUser_Id(saved.getId());
        if (null == token) {
            token = new EToken();
            token.setUser(saved);
        }
        Date now = new Date();
        token.setToken(StringUtil.toMD5HexString(MiquanerApplication.DEFAULT_DATE_FORMAT.format(now)));
        token.setExpired(DateUtils.add(now, Calendar.DAY_OF_YEAR, 3));
        EToken eToken = this.tokenRepo.save(token);

        return tokenWithUser(eToken, saved);
    }

    private boolean setSuperDivider(UserRegisterSubmit userRegisterSubmit) {
        EConfig config = this.configRepo.findByKeyEquals(AliMaMaSubmit.PLATFORM_CODE);
        if (null == config) {
            return false;
        }
        if (config.getValue().equals(userRegisterSubmit.getInvitation())) {

            userRegisterSubmit.getUser().setExt("platform_user");
            return true;
        }
        return false;
    }

    @Override
    public Object resetPwd(UserResetPwdSubmit userResetPwdSubmit) {
        Object x = checkSmsCode(userResetPwdSubmit.getPhone(), userResetPwdSubmit.getSmsCode());
        if (x != null) return x;

        EUser byPhoneEquals = this.userRepo.findByPhoneEquals(userResetPwdSubmit.getPhone());
        if (null == byPhoneEquals) {
            return Result.fail(new ErrorR(ErrorR.NO_ID_FOUND, ErrorR.NO_ID_FOUND_MSG));
        }

        byPhoneEquals.setPwd(userResetPwdSubmit.getPwd());
        this.userRepo.save(byPhoneEquals);

        EToken token = this.tokenRepo.findByUser_Id(byPhoneEquals.getId());
        if (null == token) {
            token = new EToken();
            token.setUser(byPhoneEquals);
        }
        Date now = new Date();
        token.setToken(StringUtil.toMD5HexString(MiquanerApplication.DEFAULT_DATE_FORMAT.format(now)));
        token.setExpired(DateUtils.add(now, Calendar.DAY_OF_YEAR, 3));
        EToken eToken = this.tokenRepo.save(token);

        return tokenWithUser(eToken, byPhoneEquals);
    }

    private Object tokenWithUser(EToken token, EUser user) {
        TokenView tokenView = new TokenView();
        BeanUtils.copyProperties(token, tokenView, "id", "admin", "user");

        tokenView.setCandidate(null == user.getpUser() || null == user.getpUser().getpUser());

        EUser eUser = new EUser();
        eUser.setId(user.getId());
        eUser.setName(user.getName());
        eUser.setPhone(user.getPhone());
        eUser.setAliPid(user.getAliPid());
        eUser.setCode(user.getCode());
        tokenView.setUser(eUser);

        return Result.success(tokenView);
    }

    private Object checkSmsCode(String phone, String code) {
        ESmsCode smsCode = this.smsCodeRepo.findByPhoneEquals(phone);
        if (null == smsCode) {
            return Result.fail(new ErrorR(ErrorR.NO_VERIFY_CODE, NO_VERIFY_CODE));
        }

        if (smsCode.getExpired().before(new Date())) {
            return Result.fail(new ErrorR(ErrorR.VERIFY_CODE_EXPIRED, VERIFY_CODE_EXPIRED));
        }

        if (!smsCode.getCode().equals(code)) {
            return Result.fail(new ErrorR(ErrorR.WRONG_VERIFY_CODE, WRONG_VERIFY_CODE));
        }
        return null;
    }

    @Override
    public Object sendVerifyCode(String phone) {
        phone = phone.replaceAll("\"", "");
        if (!(phone.startsWith("1") && phone.length() == 11)) {
            return Result.fail(new ErrorR(ErrorR.NO_CORRECT_PHONE, NO_CORRECT_PHONE));
        }

        ESmsCode smsCode = this.smsCodeRepo.findByPhoneEquals(phone);
        if (null == smsCode) {
            smsCode = new ESmsCode();
            smsCode.setPhone(phone);
        }
        smsCode.setCode(("" + Math.random()).substring(2, 8));
        smsCode.setExpired(DateUtils.add(new Date(), Calendar.MINUTE, 30));
        smsCode = this.smsCodeRepo.save(smsCode);

        //组装请求对象
        SendSmsRequest request = new SendSmsRequest();
        //使用post提交
        request.setMethod(MethodType.POST);
        //必填:待发送手机号。支持以逗号分隔的形式进行批量调用，批量上限为1000个手机号码,批量调用相对于单条调用及时性稍有延迟,验证码类型的短信推荐使用单条调用的方式
        request.setPhoneNumbers(phone);
        //必填:短信签名-可在短信控制台中找到
        request.setSignName("觅券儿");
        //必填:短信模板-可在短信控制台中找到
        request.setTemplateCode("SMS_109415215");
        //可选:模板中的变量替换JSON串,如模板内容为"亲爱的${name},您的验证码为${code}"时,此处的值为
        //友情提示:如果JSON中需要带换行符,请参照标准的JSON协议对换行符的要求,比如短信内容中包含\r\n的情况在JSON中需要表示成\\r\\n,否则会导致JSON在服务端解析失败
        request.setTemplateParam("{\"code\":\"" + smsCode.getCode() + "\"}");
        //可选-上行短信扩展码(扩展码字段控制在7位或以下，无特殊需求用户请忽略此字段)
        //request.setSmsUpExtendCode("90997");
        //可选:outId为提供给业务方扩展字段,最终在短信回执消息中将此值带回给调用者
        //request.setOutId("yourOutId");
        //请求失败这里会抛ClientException异常
        SendSmsResponse sendSmsResponse = null;
        try {
            sendSmsResponse = acsClient.getAcsResponse(request);
        } catch (ClientException e) {
            logger.error("send sms failed.");
            return Result.fail(null);
        }
        if (sendSmsResponse.getCode() != null && sendSmsResponse.getCode().equals("OK")) {
            //请求成功
            logger.info("send sms successfully");
            return Result.success(null);
        }

        logger.error("send sms failed.");
        return Result.fail(null);
    }

    @Override
    public Object enroll(EUser user, EnrollSubmit enrollSubmit) {
        EUser one = this.userRepo.findOne(user.getId());
        if (null == one) {
            return Result.fail(new ErrorR(ErrorR.NO_ID_FOUND, ErrorR.NO_ID_FOUND_MSG));
        }

        BeanUtil.copyNotNullProps(enrollSubmit, one);
        this.userRepo.save(one);
        return Result.success(null);
    }

    @Override
    public Object check(Long id, String aliPid) {
        EUser one = this.userRepo.findOne(id);
        if (null == one) {
            return Result.fail(new ErrorR(ErrorR.NO_ID_FOUND, ErrorR.NO_ID_FOUND_MSG));
        }

        if (null != one.getpUser() && null != one.getpUser().getpUser()) {
            return Result.fail(new ErrorR(ErrorR.THIRD_CAN_NOT_ENROLL, THIRD_CAN_NOT_ENROLL));
        }

        one.setAliPid(aliPid);
        one.setCode(("" + Math.random()).substring(2, 8));
        this.userRepo.save(one);
        clearToken(id);

        EUser view = new EUser();
        BeanUtils.copyProperties(one, view, "pwd", "pUser", "cUsers", "withdraws", "sentMails", "receivedMails", "createdMessages");
        return Result.success(view);
    }

    @Override
    public Object downGrade(Long id) {
        EUser one = this.userRepo.findOne(id);
        if (null == one) {
            return Result.fail(new ErrorR(ErrorR.NO_ID_FOUND, ErrorR.NO_ID_FOUND_MSG));
        }

        one.setAliPid(null);
        one.setCode(null);
        this.userRepo.save(one);
        clearToken(id);

        EUser view = new EUser();
        BeanUtils.copyProperties(one, view, "pwd", "pUser", "cUsers", "withdraws", "sentMails", "receivedMails", "createdMessages");
        return Result.success(view);
    }

    @Override
    @Transactional
    public List<EUser> getChildUsers(EUser user) {
        EUser one = this.userRepo.findOne(user.getId());
        return new ArrayList<>(one.getcUsers());
    }

    @Override
    public Object listAllUsers(Integer pageNo) {
        return Result.success(this.userRepo.findAll(new PageRequest(Math.max(0, pageNo - 1), 10, new Sort(Sort.Direction.ASC, "id"))).map(user -> {
            EUser viewUser = new EUser();
            BeanUtils.copyProperties(user, viewUser, "pUser", "cUsers", "withdraws", "sentMails", "receivedMails", "createdMessages");
            return viewUser;
        }));
    }

    @Override
    public boolean exportAll(String filePath) {
        List<List<String>> data = this.userRepo.findAll().stream().map(this.eUserRFunction).collect(Collectors.toList());
        data.add(0, this.getHeaders());
        return writeFile(filePath, data, this.getColWidth());
    }

    private List<Integer> getColWidth() {
        return Arrays.asList(9, 12, 9, 18, 20, 15, 20, 42, 36, 11, 10);
    }

    private List<String> getHeaders() {
        return Arrays.asList("ID", "用户名", "姓名", "电话", "支付宝", "QQ", "微信", "申请理由", "PID", "邀请码", "其它");
    }

    private boolean writeFile(String filePath, List<List<String>> data, List<Integer> width) {
        try (HSSFWorkbook wb = new HSSFWorkbook()) {
            HSSFSheet s = wb.createSheet();
            wb.setSheetName(0, "User");

            HSSFCellStyle cs = wb.createCellStyle();
            HSSFCellStyle cs2 = wb.createCellStyle();
            HSSFFont f = wb.createFont();
            HSSFFont f2 = wb.createFont();

            f.setFontHeightInPoints((short) 12);
            f.setColor(IndexedColors.BLACK.getIndex());
            f.setBold(true);
            f2.setFontHeightInPoints((short) 10);
            f2.setColor(IndexedColors.GREY_80_PERCENT.getIndex());
            f2.setBold(false);
            cs.setFont(f);
            cs2.setFont(f2);

            int rowNum = 0, cellNum;
            HSSFRow r = s.createRow(rowNum);
            r.setHeight((short) 0x128);

            HSSFCell c = null;
            List<String> headers = data.get(0);
            for (cellNum = 0; cellNum < headers.size(); cellNum++) {
                c = r.createCell(cellNum);
                c.setCellStyle(cs);
                c.setCellValue(headers.get(cellNum));

                s.setColumnWidth(cellNum, (int) (width.get(cellNum) * 16 / 0.05));
            }

            for (rowNum = 1; rowNum < data.size(); rowNum++) {
                r = s.createRow(rowNum);
                List<String> datum = data.get(rowNum);

                for (cellNum = 0; cellNum < datum.size(); cellNum++) {
                    c = r.createCell(cellNum);
                    c.setCellStyle(cs2);
                    c.setCellValue(datum.get(cellNum));
                }
            }

            try (FileOutputStream out = new FileOutputStream(filePath)) {
                wb.write(out);
                return true;
            }
        } catch (IOException e) {
            logger.error(e);
            return false;
        }
    }

    private Object clearToken(Long id) {
        EToken token = this.tokenRepo.findByUser_Id(id);
        if (null == token) {
            return Result.success(null);
        }
        token.setExpired(new Date());
        this.tokenRepo.save(token);
        return Result.success(null);
    }

}
