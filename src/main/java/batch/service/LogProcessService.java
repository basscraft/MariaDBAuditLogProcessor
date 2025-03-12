package batch.service;

import batch.utils.CommonUtils;
import batch.common.Constants;
import batch.mapper.ServerAuditMapper;
import ch.qos.logback.core.pattern.ConverterUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LogProcessService {

    private final ServerAuditMapper serverAuditMapper;
    private StopWatch stopWatch;

    @Value("${log.audit.path}")
    private String auditLogPath;
    @Value("${log.audit.file.prefix}")
    private String auditLogFilePrefix;
    @Value("${log.audit.file.ext}")
    private String auditLogFileExt;

    // 해당일 로그 리스트
    private List<Map<String,Object>> logList;

    private void addPrivatePolicyCount (Map<String, Object> auditLog, String privatePolicyType) {
        Map<String,Object> log = null;
        for (Map<String, Object> stringObjectMap : logList) {
            log = stringObjectMap;
            if (log.get("connectionId") == auditLog.get("connectionId")) {
                Map<String, Object> privatePolicy = (Map<String, Object>) log.get("privatePolicy");
                if (null == privatePolicy) {
                    privatePolicy = new HashMap<>();
                    privatePolicy.put("startTimestamp", auditLog.get("queryTimestamp"));
                }
                switch (privatePolicyType) {
                    case Constants.AES256_DECRYPT :
                        privatePolicy.put("queryId", auditLog.get("queryId"));
                        privatePolicy.put("query", auditLog.get("query"));
                        privatePolicy.put("endTimestamp", auditLog.get("queryTimestamp"));

                        /**
                         * 한번의 커넥션에 개인정보 조회 쿼리를 여러번 실행 할 수 있기 때문에
                         * 최종 쿼리에서 리스트로 담는다.
                         */
                        List<Map<String, Object>> privatePolicyList = (List<Map<String,Object>>)log.get("privatePolicyList");
                        if(null == privatePolicyList) {
                            privatePolicyList = new ArrayList<Map<String,Object>>();
                        }
                        privatePolicyList.add(privatePolicy);
                        log.put("privatePolicyList", privatePolicyList); // 리스트에 추가
                        log.remove("privatePolicy"); // 실행완료된 쿼리 삭제
                        break;
                    case Constants.AES_DECRYPT :
                        privatePolicy.put("policyCount", CommonUtils.castInt(privatePolicy.get("policyCount")) + 1);
                        log.put("privatePolicy", privatePolicy);
                        break;
                }
            }
        }

        if(null == log) {
            getSavedLog(auditLog);
        }
    }

    private void getSavedLog(Map<String, Object> auditLog) {
        Map<String,Object> savedLogs = serverAuditMapper.selectOneServerAuditMasterByConnectionId(auditLog);
        if(null != savedLogs) {

            logList.add(savedLogs);
        } else {
            logList.add(auditLog);
        }
    }

    /**
     * server audit log를 분석 해서 DB에 입력.
     * @param logFileName
     * @throws IOException
     */
    private void parseDbAuditLog(String logFileName) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(logFileName))));
            logList = new ArrayList<>();

            String rawLog;
            int count = 0;
            while ((rawLog = reader.readLine()) != null) {
                /**
                 *  server_audit_log file format
                 *  MariaDB server_audit_log format 참고
                 *  https://mariadb.com/kb/en/mariadb-audit-plugin-log-format/
                 *
                 *  1. 각 필드는 콤마로 분리
                 *  2. 파일로 저장하는 경우 필드 구성 (server_audit_output_type=file)
                 *    - [0] timestamp : 이벤트 발생 시간 (기본 포멧은 YYYYMMDD HH:mm:dd)
                 *    - [1] serverhost : 이벤트가 발생한 DB서버 hostname
                 *    - [2] username : 사용자 ID
                 *    - [3] host : 사용자 IP
                 *    - [4] connectionid : 사용자 접속 ID (커넥션 별로 다른 듯)
                 *    - [5] queryid : 쿼리ID
                 *          테이블에 대한 여러 이벤트의 경우 다수의 로우가 생성됨
                 *          operation이 CONNECT, DISCONNECT, FAILED_CONNECT인 경우 0
                 *    - [6] operation : 작업유형 (CONNECT, QUERY, READ, WRITE, CREATE, ALTER, RENAME, DROP)
                 *    - [7] database : 사용한 DB명
                 *    - [8] object :
                 *          operation이 QUERY 인경우 수행한 쿼리 (싱글 쿼테이션으로 감싸 있으며 내부에 콤마를 포함할 수 있음)
                 *          테이블 변경 인경우 테이블 명
                 *          operation이 CONNECT, DISCONNECT, FAILED_CONNECT인 경우 빈값
                 *    - [9] retcode : 로그 실행 결과 코드
                 */
                try {
                    log.debug("rawLog : {}", rawLog);
                    String[] logColumns = rawLog.split(",");
                    String timestamp = logColumns[0];
                    String serverHost = logColumns[1];
                    String userName = logColumns[2];
                    String host = logColumns[3];
                    long connectionId = CommonUtils.castLong(logColumns[4]);
                    long queryId = CommonUtils.castLong(logColumns[5]);
                    String operation = logColumns[6];
                    String database = logColumns[7];
                    String object;
                    int retCode;

                    /**
                     * 아래 계정은 로그를 저장하지 않을 계정 나열 (제외 계정)
                     switch (userName) {
                     case "dba": // ebmp 서비스용 계정
                     case "server_audit": // 서버 감사로그용 계정
                     continue;
                     }
                     */

                    Map<String, Object> auditLog = new HashMap<String, Object>();
                    auditLog.put("connectionId", connectionId);
                    switch (operation) {
                        case Constants.OPERATION_QUERY:
                            object = rawLog.substring(rawLog.indexOf(",", rawLog.indexOf(operation) + (operation.length() + 1)) + 1, rawLog.lastIndexOf(","));
                            auditLog.put("userName", userName);
                            auditLog.put("userHost", host);
                            auditLog.put("queryId", queryId);
                            auditLog.put("queryTimestamp", timestamp);
                            if (object.toUpperCase().indexOf(Constants.AES_DECRYPT) > 0) {
                                addPrivatePolicyCount(auditLog, Constants.AES_DECRYPT);
                            }
                            if (object.toUpperCase().indexOf(Constants.AES256_DECRYPT) > 0) {
                                auditLog.put("query", object);
                                addPrivatePolicyCount(auditLog, Constants.AES256_DECRYPT);
                            }
                            retCode = CommonUtils.castInt(logColumns[logColumns.length - 1]);
                            break;
                        case Constants.OPERATION_CONNECT:
                            object = logColumns[8];
                            auditLog.put("userName", userName);
                            auditLog.put("userHost", host);
                            auditLog.put("databaseName", database);
                            auditLog.put("connectTimestamp", timestamp);
                            /**
                             * 로그가 하루 단위로 rotate 되기 때문에 이전에 저장된 로그 정보가 있으면 DB 값을 기준으로 처리
                             */
                            retCode = CommonUtils.castInt(logColumns[logColumns.length - 1]);
                            getSavedLog(auditLog);
                        case Constants.OPERATION_DISCONNECT:
                            auditLog.put("disConnectTimestamp", timestamp);
                            setDisconnectTimestamp(auditLog);
                            retCode = CommonUtils.castInt(logColumns[logColumns.length - 1]);
                            break;
                        case Constants.OPERATION_FAILED_CONNECT:
                            auditLog.put("disConnectTimestamp", timestamp);
                            retCode = CommonUtils.castInt(logColumns[logColumns.length - 1]);
                            break;
                        default:
                            continue; // 삭제 후 재실행 로그 수 다름
                    }
                } catch (Exception e) {
                    log.error("log parse error", e);
                    continue; // 에러가 발생한 건은 스킵
                }
                count++;
            }

            if (null == logList || logList.isEmpty()) return;

            if (log.isDebugEnabled()) {
                log.debug("log count : {}", count);
                log.debug("log list : {}", logList);
            }

            Map<String, Object> param = new HashMap<>();
            param.put("list", logList);
            serverAuditMapper.insertServerAuditMaster(param);
            for (Map<String, Object> item : logList) {
                /**
                 * 로그가 하루 단위로 rotate 되기 때문에
                 * 중간에 걸쳐서 실행된 로그가 있는 경우 진행 중인 상태로 DB에 저장
                 */
                List<Map<String, Object>> privatePolicyList = (List<Map<String, Object>>) item.get("privatePolicyList");
                if (null == privatePolicyList) {
                    privatePolicyList = new ArrayList<>();
                }
                Map<String, Object> incomplete = (Map<String, Object>) item.get("privatePolicy");
                if (null != incomplete) {
                    incomplete.put("connectionId", item.get("connectionId"));
                    serverAuditMapper.insertIncompleteQuery(incomplete);
                }
                for (Map<String, Object> privatePolicy : privatePolicyList) {
                    privatePolicy.put("connectionId", item.get("connectionId"));
                }
                if (!privatePolicyList.isEmpty()) {
                    if (log.isDebugEnabled()) {
                        log.debug("private policy list : {}", privatePolicyList);
                    }
                    param = new HashMap<>();
                    param.put("list", privatePolicyList);
                    serverAuditMapper.insertPrivatePolicyHist(param);
                }
            }
        } catch (FileNotFoundException e) {
            log.info("log file not found : {}", logFileName);
        } catch (Exception e) {
            // to-do : send alarm if you need, Implementation is required.
            log.error("log parse error", e);
            //throw new RuntimeException(e);
        } finally {
            try {
                if(reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * DB 접속 종료 시간 주입
     * @param auditLog
     */
    private void setDisconnectTimestamp(Map<String, Object> auditLog) {
        for(Map<String,Object> item: logList) {
            if(item.get("connectionId") == auditLog.get("connectionId")) {
                item.put("disConnectTimestamp", auditLog.get("disConnectTimestamp"));
            }
        }
    }

    /**
     * 실행 시간 기준 전일자 로그 파일에 대해 로그 분석 수행 (메인 함수)
     */
    public void auditLogParse() {
        stopWatch = new StopWatch();
        stopWatch.start();

        //String currentDate = CommonUtils.getCurrentDateString("yyyyMMdd");
        String currentDate = CommonUtils.getYesterdayDateString("yyyyMMdd");
        //String currentDate = "20250305"; // for test

        String logFileName = auditLogPath + File.separator + auditLogFilePrefix + "-" + currentDate + auditLogFileExt;

        if(log.isDebugEnabled()) {
            log.debug("auditLogPath : {}", auditLogPath);
            log.debug("auditLogFilePrefix : {}", auditLogFilePrefix);
            log.debug("auditLogFileExt : {}", auditLogFileExt);
            log.debug("currentDate : {}", currentDate);
            log.debug("logFileName : {}", logFileName);
        }
        parseDbAuditLog(logFileName);

        stopWatch.stop();
        log.info("#### auditLogParse parse success : {}", stopWatch.getTotalTimeMillis());
    }

    public void auditLogParseAll() {
        stopWatch = new StopWatch();
        stopWatch.start();

        if(log.isDebugEnabled()) {
            log.debug("auditLogPath : {}", auditLogPath);
            log.debug("auditLogFilePrefix : {}", auditLogFilePrefix);
            log.debug("auditLogFileExt : {}", auditLogFileExt);
        }

        File auditLogDir = new File(auditLogPath);
        File[] auditFiles = auditLogDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File auditLogDir, String name) {
                return name.endsWith(auditLogFileExt);
            }
        });

        assert auditFiles != null;
        for(File auditFile : auditFiles) {
            String logFileName = auditFile.getPath();
            log.info("auditLogFile : {}", logFileName);
            parseDbAuditLog(logFileName);
        }

        stopWatch.stop();
        log.info("#### auditLogParseAll parse success : {}", stopWatch.getTotalTimeMillis());
    }

}
