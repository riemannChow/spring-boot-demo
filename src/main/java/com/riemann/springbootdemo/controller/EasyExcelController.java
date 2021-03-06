package com.riemann.springbootdemo.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelReader;
import com.alibaba.excel.read.metadata.ReadSheet;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.riemann.springbootdemo.dao.EasyExcelDao;
import com.riemann.springbootdemo.enums.ReturnEnum;
import com.riemann.springbootdemo.listener.EasyExcelListener;
import com.riemann.springbootdemo.model.ApiResult;
import com.riemann.springbootdemo.model.EasyExcelData;
import com.riemann.springbootdemo.model.LocatorData;
import com.riemann.springbootdemo.model.LocatorDataJsonToListEntity;
import com.riemann.springbootdemo.service.EasyExcelService;
import com.riemann.springbootdemo.util.JsonUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author riemann
 * @date 2019/12/19 22:10
 */
@Api(description = "上传、下载EasyExcel接口")
@RestController
public class EasyExcelController {

    private static final Logger LOGGER = LoggerFactory.getLogger(EasyExcelController.class);

    @Autowired
    private EasyExcelService ueeService;

    @Resource
    private EasyExcelDao easyExcelDao;

    @ApiOperation(value = "上传excel", notes = "通过easy excel上传excel到db")
    @ApiImplicitParam(name = "serviceFile", value = "excel文件", paramType = "save", required = true)
    @PostMapping(value = "/uploadEasyExcel",produces = "application/json;charset=UTF-8")
    public ApiResult uploadEasyExcel(@RequestParam(value = "serviceFile") MultipartFile serviceFile) {
        if (serviceFile == null) {
            return new ApiResult(ReturnEnum.PARAM_ERROR, "serviceFile can not be null");
        }
        ExcelReader excelReader = null;
        InputStream in = null;
        try {
            in = serviceFile.getInputStream();
            excelReader = EasyExcel.read(in, EasyExcelData.class,
                    new EasyExcelListener(ueeService)).build();
            ReadSheet readSheet = EasyExcel.readSheet(0).build();
            excelReader.read(readSheet);
        } catch (IOException e) {
            LOGGER.error("import excel to db fail", e);
        } finally {
            close(in);
            // 这里一定别忘记关闭，读的时候会创建临时文件，到时磁盘会崩
            if (excelReader != null) {
                excelReader.finish();
            }
        }
        return new ApiResult("upload easyexcel success");
    }

    private void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                LOGGER.error("Close io stream error", e);
            }
        }
    }

    @ApiOperation(value = "下载excel", notes = "通过easy excel从db下载数据到excel")
    @GetMapping(value = "/downloadEasyExcel",produces = "application/json;charset=UTF-8")
    public void downloadEasyExcel(HttpServletResponse response) throws IOException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String datetime = sdf.format(new Date());
        String fileName = URLEncoder.encode("下载excel服务", "UTF-8") + datetime;

        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-disposition", "attachment;filename=" + fileName + ".xlsx");

        // excel头策略
        WriteCellStyle headWriteCellStyle = new WriteCellStyle();
        WriteFont headWriteFont = new WriteFont();
        headWriteFont.setFontHeightInPoints((short) 11);
        headWriteFont.setBold(false);
        headWriteCellStyle.setWriteFont(headWriteFont);

        // excel内容策略
        WriteCellStyle contentWriteCellStyle = new WriteCellStyle();
        WriteFont contentWriteFont = new WriteFont();
        contentWriteFont.setFontHeightInPoints((short)11);
        contentWriteCellStyle.setWriteFont(contentWriteFont);

        // 设置handler
        HorizontalCellStyleStrategy styleStrategy =
                new HorizontalCellStyleStrategy(headWriteCellStyle, contentWriteCellStyle);

        List<EasyExcelData> easyExcelData = ueeService.selectAll();

        EasyExcel.write(response.getOutputStream(), EasyExcelData.class)
                .sheet("下载excel服务")
                .registerWriteHandler(styleStrategy)
                .doWrite(easyExcelData);
    }

    @PostMapping(value = "/uploadFileFromJson",produces = "application/json;charset=UTF-8")
    public ApiResult uploadFileFromJson(@RequestParam(value = "mappingFile") MultipartFile mappingFile) {
        if (mappingFile == null) {
            return new ApiResult(ReturnEnum.PARAM_ERROR, "Params can not be null");
        }
        InputStream is = null;
        BufferedReader br = null;
        StringBuffer sb = new StringBuffer();
        String str = null;

        try {
            is = mappingFile.getInputStream();
            br = new BufferedReader(new InputStreamReader(is));
            while ((str = br.readLine()) != null) {
                sb.append(str);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        JSONObject jsonObject = JSONObject.parseObject(sb.toString());
        JSONArray jsonArray = jsonObject.getJSONArray("data");
        List<EasyExcelData> eeDataList = JSONObject.parseArray(jsonArray.toJSONString(), EasyExcelData.class);

        int count = 0;
        try {
            count = easyExcelDao.uploadFileFromJson(eeDataList);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (count <= 0) {
            LOGGER.warn("no data to upload");
            return new ApiResult(ReturnEnum.PARAM_ERROR, "no data to upload");
        }
        return new ApiResult("successfully imported " + count + " pieces of data");
    }

    @PostMapping(value = "/arrayObjectNestingParse",produces = "application/json;charset=UTF-8")
    public ApiResult arrayObjectNestingParse(@RequestParam(value = "jsonFile") MultipartFile jsonFile) {
        if (jsonFile == null) {
            return new ApiResult(ReturnEnum.PARAM_ERROR, "Params can not be null");
        }
        InputStream is = null;
        BufferedReader br = null;
        StringBuffer sb = new StringBuffer();
        String str = null;

        try {
            is = jsonFile.getInputStream();
            br = new BufferedReader(new InputStreamReader(is));
            while ((str = br.readLine()) != null) {
                sb.append(str);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        List<LocatorData> locatorDataList = new ArrayList<>();
        List<LocatorDataJsonToListEntity> locatorDataJsonToList = null;
        JSONArray jsonArray = JSONArray.parseArray(sb.toString());

        locatorDataJsonToList = JsonUtil.json2List(jsonArray.toString(), LocatorDataJsonToListEntity.class);
        for (LocatorDataJsonToListEntity entity : locatorDataJsonToList) {
            LocatorData locatorData = new LocatorData(entity);
            locatorDataList.add(locatorData);
        }
        LOGGER.info("locatorDataList: " + JSON.toJSONString(locatorDataList));
        return new ApiResult(JSON.toJSONString(locatorDataList));
    }
}

