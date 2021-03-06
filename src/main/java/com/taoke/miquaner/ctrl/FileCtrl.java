package com.taoke.miquaner.ctrl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mysql.jdbc.StringUtils;
import com.taobao.api.internal.toplink.embedded.websocket.util.StringUtil;
import com.taoke.miquaner.MiquanerApplication;
import com.taoke.miquaner.data.EUser;
import com.taoke.miquaner.serv.IBlogServ;
import com.taoke.miquaner.serv.IOrderServ;
import com.taoke.miquaner.serv.IShareServ;
import com.taoke.miquaner.serv.IUserServ;
import com.taoke.miquaner.util.Auth;
import com.taoke.miquaner.util.Result;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Controller
public class FileCtrl {

    private static final Logger logger = LogManager.getLogger(FileCtrl.class);

    private final Environment env;
    private final IUserServ userServ;
    private final IShareServ shareServ;
    private final IOrderServ orderServ;
    private final IBlogServ blogServ;

    @Autowired
    public FileCtrl(Environment env, IUserServ userServ, IShareServ shareServ, IOrderServ orderServ, IBlogServ blogServ) {
        this.env = env;
        this.userServ = userServ;
        this.shareServ = shareServ;
        this.orderServ = orderServ;
        this.blogServ = blogServ;
    }

    @RequestMapping(value = "/share/{key}", method = RequestMethod.GET)
    public String share(@PathVariable(name = "key") String key, Map<String, Object> map) {
        Object shareFetch = this.shareServ.shareFetch(key);
        if (shareFetch instanceof Result) {
            String params = ((Result) shareFetch).getBody().toString();
            if (StringUtils.isNullOrEmpty(params)) {
                map.put("found", false);
            } else {
                try {
                    Map readValue = MiquanerApplication.DEFAULT_OBJECT_MAPPER.readValue(params, Map.class);
                    map.put("found", true);
                    map.putAll(readValue);
                } catch (IOException e) {
                    map.put("found", false);
                }
            }
        } else {
            map.put("found", false);
        }
        return "shareHTML";
    }

    @RequestMapping(value = "/blog/{path}/{domain:.*}", method = RequestMethod.GET)
    public ModelAndView blog(@PathVariable(name = "path") String path, @PathVariable(name = "domain") String domain) {
        ModelAndView modelAndView = new ModelAndView();

        try {
            String content = this.blogServ.fetchBlog(
                    path.replaceAll("&@&", "/"),
                    domain.replaceAll("&@&", "/")
            );
            modelAndView.addObject("title", this.blogServ.parseTitle(content));
            modelAndView.addObject("content", this.blogServ.dryMarkdown(content));
            modelAndView.addObject("found", true);
        } catch (IOException e) {
            modelAndView.addObject("found", false);
        }

        modelAndView.setViewName("blogHTML");
        return modelAndView;
    }

    @Auth(isAdmin = true)
    @RequestMapping(value = "/uploadFile", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> uploadFile(@RequestParam("uploadFiles") MultipartFile[] uploadingFiles) {

        String directory = env.getProperty("taoke.paths.uploadedFiles");
        try {
            Map<String, String> ret = new HashMap<>();
            for (MultipartFile uploadedFile : uploadingFiles) {
                String filename = StringUtil.toMD5HexString(MiquanerApplication.DEFAULT_DATE_FORMAT.format(new Date()))
                        + uploadedFile.getOriginalFilename().substring(uploadedFile.getOriginalFilename().lastIndexOf('.'));
                String filepath = Paths.get(directory, filename).toString();
                File file = new File(filepath);
                uploadedFile.transferTo(file);
                ret.put(uploadedFile.getOriginalFilename(), filename);
            }
            return new ResponseEntity<>(MiquanerApplication.DEFAULT_OBJECT_MAPPER.writeValueAsString(ret), HttpStatus.OK);
        } catch (IOException e) {
            logger.error("上传文件发生I/O错误");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

    }

    @Auth
    @RequestMapping(value = "/upload/client/images", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<?> uploadClientImages(@RequestParam("uploadFiles") MultipartFile[] uploadingFiles, HttpServletRequest request) {

        EUser user = (EUser) request.getAttribute("user");
        if (null == user) {
            try {
                return new ResponseEntity<>(MiquanerApplication.DEFAULT_OBJECT_MAPPER.writeValueAsString(Result.unAuth()), HttpStatus.OK);
            } catch (JsonProcessingException e) {
                logger.error("上传文件发生I/O错误");
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        }

        try {
            Map<String, String> ret = new HashMap<>();
            for (MultipartFile uploadedFile : uploadingFiles) {
                String filePath = this.blogServ.uploadImage(user.getPhone(), uploadedFile);
                ret.put(uploadedFile.getOriginalFilename(), filePath);
            }
            return new ResponseEntity<>(MiquanerApplication.DEFAULT_OBJECT_MAPPER.writeValueAsString(Result.success(ret)), HttpStatus.OK);
        } catch (IOException e) {
            logger.error("上传文件发生I/O错误");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

    }

    @Auth(isAdmin = true)
    @RequestMapping(value = "/export/all/{showAnonymousFlag}", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> exportAll(@PathVariable("showAnonymousFlag") Integer showAnonymousFlag)
            throws IOException {
        String fileName = String.format("ALL-USERS_%s.xls", MiquanerApplication.DEFAULT_DATE_FORMAT.format(new Date()));
        String filePath = getFilePath(fileName);
        boolean suc = this.userServ.exportAll(filePath, showAnonymousFlag == 1);
        return getInputStreamResourceResponseEntity(filePath, fileName, suc);
    }

    @Auth(isAdmin = true)
    @RequestMapping(value = "/export/all/need/check", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> exportAllNeedCheck()
            throws IOException {
        String fileName = String.format("ALL-NEED-CHECK-USERS_%s.xls", MiquanerApplication.DEFAULT_DATE_FORMAT.format(new Date()));
        String filePath = getFilePath(fileName);
        boolean suc = this.userServ.exportAllNeedCheck(filePath);
        return getInputStreamResourceResponseEntity(filePath, fileName, suc);
    }

    @Auth(isAdmin = true)
    @RequestMapping(value = "/export/team/{userId}", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> exportTeam(@PathVariable("userId") Long userId)
            throws IOException {
        String fileName = String.format("ALL-TEAM-USERS_%s.xls", MiquanerApplication.DEFAULT_DATE_FORMAT.format(new Date()));
        String filePath = getFilePath(fileName);
        boolean suc = this.userServ.exportTeam(filePath, userId);
        return getInputStreamResourceResponseEntity(filePath, fileName, suc);
    }

    @Auth(isAdmin = true)
    @RequestMapping(value = "/export/search/{search}", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> exportTeam(@PathVariable("search") String search)
            throws IOException {
        String fileName = String.format("ALL-SEARCH-USERS_%s.xls", MiquanerApplication.DEFAULT_DATE_FORMAT.format(new Date()));
        String filePath = getFilePath(fileName);
        boolean suc = this.userServ.exportSearch(filePath, search);
        return getInputStreamResourceResponseEntity(filePath, fileName, suc);
    }

    @Auth(isAdmin = true)
    @RequestMapping(value = "/export/withdraw/list/{type}", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> exportWithdraw(@PathVariable(name = "type") Integer type) throws IOException {
        String fileName = String.format("ALL-WITHDRAW_%s.xls", MiquanerApplication.DEFAULT_DATE_FORMAT.format(new Date()));
        String filePath = getFilePath(fileName);
        boolean suc = this.orderServ.exportWithdraw(filePath, type);
        return getInputStreamResourceResponseEntity(filePath, fileName, suc);
    }

    @Auth(isAdmin = true)
    @RequestMapping(value = "/export/withdraw/search/{key}", method = RequestMethod.GET)
    public ResponseEntity<InputStreamResource> exportWithdraw(@PathVariable(name = "key") String key) throws IOException {
        String fileName = String.format("ALL-SEARCH-WITHDRAW_%s.xls", MiquanerApplication.DEFAULT_DATE_FORMAT.format(new Date()));
        String filePath = getFilePath(fileName);
        boolean suc = this.orderServ.exportWithdraw(filePath, key);
        return getInputStreamResourceResponseEntity(filePath, fileName, suc);
    }

    private ResponseEntity<InputStreamResource> getInputStreamResourceResponseEntity(String filePath, String fileName, boolean suc) throws IOException {
        if (!suc) {
            return ResponseEntity.status(500).body(null);
        }

        FileSystemResource file = new FileSystemResource(filePath);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Content-Disposition", String.format("attachment; filename=\"%s\"", file.getFilename()));
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");
        headers.add("fileName", fileName);

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentLength(file.contentLength())
                .contentType(MediaType.parseMediaType("application/vnd.ms-excel"))
                .body(new InputStreamResource(file.getInputStream()));
    }

    private String getFilePath(String fileName) {
        String directory = env.getProperty("taoke.paths.uploadedFiles");
        return Paths.get(directory, fileName).toString();
    }

}
