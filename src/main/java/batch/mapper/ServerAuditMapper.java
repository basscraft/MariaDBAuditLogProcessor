package batch.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ServerAuditMapper {
    Map<String,Object> selectOneServerAuditMasterByConnectionId(Map<String, Object> param);
    List<Map<String,Object>> selectPrivatePolicyHistByConnectionId(Map<String, Object> param);
    Map<String,Object> selectOneIncompleteQueryByConnectionId(Map<String, Object> param);
    void deleteIncompleteQueryByConnectionId(Map<String, Object> param);
    void insertServerAuditMaster(Map<String,Object> param);
    void insertPrivatePolicyHist(Map<String,Object> param);
    void insertIncompleteQuery(Map<String, Object> param);
}

