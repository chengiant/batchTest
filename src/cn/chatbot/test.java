package cn.chatbot;

import cn.chatbot.api.ApiAgent;
import cn.chatbot.api.ApiFeild;
import cn.chatbot.api.EasyApi;
import cn.chatbot.api.NetUtils;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sun.xml.internal.ws.util.StringUtils;
import okhttp3.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

class ThreadDealer3 implements Runnable {
    private final OkHttpClient mOkHttpClient;
    ConcurrentLinkedQueue<QA> inputqueue;
    ConcurrentLinkedQueue<QA> outputqueue;
    public static final MediaType JSONData = MediaType.parse("application/json; charset=utf-8");
    int length = 10;
    int counter = 0;
    Config config;
    EasyApi easyApi;

    public Response postDataSynToNet(String url, String jsonParams) {
        RequestBody body = RequestBody.create(JSONData, jsonParams);
        okhttp3.Request.Builder requestBuilder = new okhttp3.Request.Builder();
        Request request = requestBuilder.post(body).url(url).build();
        Call call = this.mOkHttpClient.newCall(request);
        Response response = null;

        try {
            response = call.execute();
        } catch (IOException var10) {
            var10.printStackTrace();
        }

        return response;
    }
    public ThreadDealer3(ConcurrentLinkedQueue<QA> inputqueue,
                         ConcurrentLinkedQueue<QA> outputqueue,
                         Config config) {
        this.outputqueue = outputqueue;
        this.inputqueue = inputqueue;
        this.config = config;
        HashMap<String, ApiFeild> inputParams = new HashMap<>();
        String url = config.apiAddr + "/cloud/robot/"+ config.robotId + "/answer";

        inputParams.put("question", new ApiFeild("question","问题","String"));
        inputParams.put("userId", new ApiFeild("userId","用户","String"));
        inputParams.put("sessionId", new ApiFeild("sessionId","会话","String"));
        HashMap<String, ApiFeild> outParams = new HashMap<>();
        ApiAgent apiAgent = new ApiAgent("id_api", "问答", "GET", url, inputParams, outParams);
        HashMap<String, String> transferMap = new HashMap<>();
        transferMap.put("question", "question");
        transferMap.put("userId", "userId");
        transferMap.put("sessionId", "sessionId");
        this.easyApi = new EasyApi(apiAgent, transferMap);

        // init


        OkHttpClient.Builder ClientBuilder = new OkHttpClient.Builder();
        ClientBuilder.readTimeout(20L, TimeUnit.SECONDS);
        ClientBuilder.connectTimeout(6L, TimeUnit.SECONDS);
        ClientBuilder.writeTimeout(60L, TimeUnit.SECONDS);
        ClientBuilder.hostnameVerifier(new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
        this.mOkHttpClient = ClientBuilder.build();

    }

    public void run() {
        while (true) {
            QA qa = inputqueue.poll();
            if (qa != null) {
                String sessionId = System.nanoTime() + Math.random() * 1000 + "";

                //init
                String init_url = config.apiAddr + "/api/robot/"+ config.robotId + "/session/init";
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("robotId", config.robotId);
                jsonObject.put("userId", config.userId);
                jsonObject.put("sessionId", sessionId);
                JSONObject jsonObject1 = new JSONObject();
                jsonObject1.put("userId", config.userId);
                jsonObject.put("initData", jsonObject1);
                String inits = jsonObject.toJSONString();
                postDataSynToNet(init_url, inits);



                /// call api
                HashMap<String, String> queryParams = new HashMap<>();
                queryParams.put("question", qa.question);
                queryParams.put("sessionId", sessionId);
                queryParams.put("userId", config.userId);
                easyApi.call(queryParams);
                JSONObject object = (JSONObject)easyApi.getData();
                QA newQA = new QA(qa.question, qa.index);
                JSONArray answers = object.getJSONArray("answers");
                if (answers.size() > 0){
                    JSONObject ans = answers.getJSONObject(0);
                    newQA.answer = ans.getString("answer");
                    newQA.score = ans.getDouble("score");
                    newQA.std = ans.getString("question");
                    JSONArray sugs = ans.getJSONArray("suggestions");
                    for (int j = 0; j < sugs.size(); j++) {
                        newQA.suggestions.add(sugs.get(j).toString());
                    }

                    JSONArray choices = ans.getJSONArray("choices");
                    for (int j = 0; j < choices.size(); j++) {
                        newQA.choices.add(choices.get(j).toString());
                    }
                }else {
                    newQA.answer = "接口未找到答案";
                }

                outputqueue.add(newQA);
            }else {
                break;
            }
        }
    }
}

class Config{
    public String apiAddr="";
    public String robotId="";
    public int threadnum= 0 ;
    public String inputAddr="";
    public String outputAddr="";
    public String userId = "";
    public void load() throws IOException {
        String encoding = "UTF8";
        File file_src = new File("config.txt");
        if (file_src.isFile() && file_src.exists()) {
            InputStreamReader read = null;
            try {
                read = new InputStreamReader(new FileInputStream(file_src), encoding);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            BufferedReader bufferedReader = new BufferedReader(read);
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                if (line.length() > 0) {
                    if (line.contains("robotApiAddress")){
                        String[] cc = line.split("=");
                        if (cc.length == 2){
                            apiAddr = cc[1].trim();
                        }
                    }

                    if (line.contains("robotId")){
                        String[] cc = line.split("=");
                        if (cc.length == 2){
                            robotId = cc[1].trim();
                        }
                    }

                    if (line.contains("input")){
                        String[] cc = line.split("=");
                        if (cc.length == 2){
                            inputAddr = cc[1].trim();
                        }
                    }

                    if (line.contains("output")){
                        String[] cc = line.split("=");
                        if (cc.length == 2){
                            outputAddr = cc[1].trim();
                        }
                    }
                    if (line.contains("userId")){
                        String[] cc = line.split("=");
                        if (cc.length == 2){
                            userId = cc[1].trim();
                        }
                    }

                    if (line.contains("thread")){
                        String[] cc = line.split("=");
                        if (cc.length == 2){
                            threadnum = Integer.valueOf(cc[1].trim());
                        }
                    }
                }

            }
        }

    }
    public Config(){

    }
}
class QA{
    int index = 0;
    String question = "";
    String std = "";
    double score = 0f;
    String answer = "";
    List<String> choices = new ArrayList<>();
    List<String> suggestions = new ArrayList<>();
    public QA(){}
    public QA(String question, int index){
        this.question = question;
        this.index = index;
    }
}

public class test {
    public static List<List<String>> readXlsx(String path) throws Exception {
        InputStream is = new FileInputStream(path);
        HSSFWorkbook xssfWorkbook = new HSSFWorkbook(is);
        List<List<String>> result = new ArrayList<List<String>>();
        // 循环每一页，并处理当前循环页
        for (Sheet xssfSheet : xssfWorkbook) {
            if (xssfSheet == null) {
                continue;
            }
            // 处理当前页，循环读取每一行
            for (int rowNum = 1; rowNum <= xssfSheet.getLastRowNum(); rowNum++) {
                Row xssfRow = xssfSheet.getRow(rowNum);
                int minColIx = xssfRow.getFirstCellNum();
                int maxColIx = xssfRow.getLastCellNum();
                List<String> rowList = new ArrayList<String>();
                for (int colIx = minColIx; colIx < maxColIx; colIx++) {
                    Cell cell = xssfRow.getCell(colIx);
                    if (cell == null) {
                        continue;
                    }
                    rowList.add(cell.toString());
                }
                result.add(rowList);
            }
        }
        return result;
    }

    public static List<QA> readQuestion(String path) throws Exception {
        InputStream is = new FileInputStream(path);
        HSSFWorkbook xssfWorkbook = new HSSFWorkbook(is);
        List<QA> result = new ArrayList<>();
        // 循环每一页，并处理当前循环页
        for (Sheet xssfSheet : xssfWorkbook) {
            if (xssfSheet == null) {
                continue;
            }
            // 处理当前页，循环读取每一行
            for (int rowNum = 1; rowNum <= xssfSheet.getLastRowNum(); rowNum++) {
                Row xssfRow = xssfSheet.getRow(rowNum);
                int minColIx = xssfRow.getFirstCellNum();
                int maxColIx = xssfRow.getLastCellNum();
                List<String> rowList = new ArrayList<String>();
                Cell cell = xssfRow.getCell(0);
                result.add(new QA(cell.toString(), rowNum - 1));
            }
        }
        return result;
    }
    private static   void add_sorted_list (List<QA> list, QA ex, int max_size) {
        int index = list.size();
        for (int i = 0; i < list.size(); i++) {
            QA tt = list.get(i);
            if (tt.index > ex.index) {
                index = i;
                break;
            }
        }
        if (max_size == -1 || list.size() < max_size) {
            if (index == list.size()) {
                list.add(ex);
            }else {
                list.add(index, ex);
            }
        }else {
            if (list.size() == max_size) {
                if (index < list.size()) {
                    list.remove(list.size()-1);
                    if (index == list.size()) {
                        list.add(ex);
                    }else {
                        list.add(index, ex);
                    }
                }
            }
        }
    }

    /**
     * 创建Excel文件
     * @param filepath filepath 文件全路径
     * @param sheetName 新Sheet页的名字
     * @param titles 表头
     * @param values 每行的单元格
     */
    public static boolean writeExcel(String filepath, String sheetName,
                                     List<QA> values) throws IOException {
        boolean success = false;
        List<String> titles = new ArrayList<>();
        titles.add("问题");titles.add("命中标准问");titles.add("答案");titles.add("推荐");titles.add("选项");titles.add("得分");
        OutputStream outputStream = null;
        if (false) {
            throw new IllegalArgumentException("文件路径不能为空");
        } else {
            Workbook workbook;
            workbook = new HSSFWorkbook();
            // 生成一个表格
            Sheet sheet;
            sheet = workbook.createSheet(sheetName);
            // 设置表格默认列宽度为15个字节
            sheet.setDefaultColumnWidth((short) 15);
            // 创建标题行
            Row row = sheet.createRow(0);
            // 存储标题在Excel文件中的序号
            for (int i = 0; i < titles.size(); i++) {
                Cell cell = row.createCell(i);
                String title = titles.get(i);
                cell.setCellValue(title);
            }
            // 写入正文
            // 行号
            int index = 1;
            for (int j = 0; j< values.size(); j++){
                row = sheet.createRow(index);
                QA qa = values.get(j);
                Cell cell = row.createCell(0);
                cell.setCellValue(qa.question);

                cell = row.createCell(1);
                cell.setCellValue(qa.std);

                cell = row.createCell(2);
                cell.setCellValue(qa.answer);

                cell = row.createCell(3);
                String sus = "";
                for (int k = 0; k< qa.suggestions.size(); k++){
                    sus = sus + qa.suggestions.get(k)+ "\n";
                }
                cell.setCellValue(sus);

                String choices = "";
                for (int k = 0; k< qa.choices.size(); k++){
                    choices = choices + qa.choices.get(k)+ "\n";
                }
                cell = row.createCell(4);
                cell.setCellValue(choices);

                cell = row.createCell(5);
                cell.setCellValue(qa.score);
                index++;
            }

            try {
                outputStream = new FileOutputStream(filepath);
                workbook.write(outputStream);
                success = true;
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (workbook != null) {
                    workbook.close();
                }
            }
            return success;
        }
    }


    public static void main(String[] args) throws Exception {
        List<String> questions = new ArrayList<>();
        Config config = new Config();
        config.load();

        List<QA> question = readQuestion(config.inputAddr);
        String encoding = "UTF8";
        ConcurrentLinkedQueue<QA> input = new ConcurrentLinkedQueue<>();
        input.addAll(question);
        ConcurrentLinkedQueue<QA> output = new ConcurrentLinkedQueue<>();
        Thread.sleep(1000);
        for (int j = 0; j < config.threadnum; j++) {
            Thread t = new Thread(new ThreadDealer3(input, output, config));
            t.setDaemon(true);
            t.start();//多线程往队列中写入数据
        }
        while (!input.isEmpty()) {
            try {
                Thread.sleep(1000);
                System.out.printf("%c ============>  processed  sentences: %d ============> ", 13, output.size());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Thread.sleep(5000);
        List<QA> qas = new ArrayList<>();
        while (true){
            QA qq  = output.poll();
            if (qq == null){
                break;
            }else {
                add_sorted_list(qas, qq, -1);
            }
        }
        writeExcel(config.outputAddr, "批测结果", qas);
        System.out.println("done!");

    }
}
