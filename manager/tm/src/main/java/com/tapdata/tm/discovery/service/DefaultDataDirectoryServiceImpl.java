package com.tapdata.tm.discovery.service;
import com.google.common.collect.Lists;

import com.mongodb.ConnectionString;
import com.tapdata.tm.commons.schema.DataSourceDefinitionDto;
import com.tapdata.tm.metadatadefinition.service.MetadataDefinitionService;

import com.tapdata.tm.commons.schema.DataSourceConnectionDto;
import com.tapdata.tm.config.security.UserDetail;
import com.tapdata.tm.ds.service.impl.DataSourceDefinitionService;
import com.tapdata.tm.ds.service.impl.DataSourceService;
import com.tapdata.tm.metadatadefinition.dto.MetadataDefinitionDto;
import com.tapdata.tm.utils.MongoUtils;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


@Service
@Setter(onMethod_ = {@Autowired})
public class DefaultDataDirectoryServiceImpl implements DefaultDataDirectoryService {
    private DataSourceService dataSourceService;
    private DataSourceDefinitionService definitionService;
    private MetadataDefinitionService metadataDefinitionService;

    @Override
    public void addConnection(String connectionId, UserDetail user) {
        DataSourceConnectionDto connectionDto = dataSourceService.findById(MongoUtils.toObjectId(connectionId), user);
        addConnection(connectionDto, user);
    }

    @Override
    public void removeConnection(String connectionId, UserDetail user) {
        boolean exists = checkConnExists(connectionId, user);
        if (!exists) {
            return;
        }

        Criteria criteriaC = Criteria.where("item_type").is("default").and("linkId").is(connectionId);
        Query removeQuery = new Query(criteriaC);
        metadataDefinitionService.deleteAll(removeQuery, user);
    }

    private boolean checkConnExists(String connectionId, UserDetail user) {
        //检查是否已经存在
        Criteria criteriaC = Criteria.where("item_type").is("default").and("linkId").is(connectionId);
        long count = metadataDefinitionService.count(new Query(criteriaC), user);
        if (count > 0) {
            return true;
        }
        return false;
    }

    @Override
    public void addConnection(DataSourceConnectionDto connectionDto, UserDetail user) {
        //检查是否已经存在
        boolean exists = checkConnExists(connectionDto.getId().toHexString(), user);
        if (exists) {
            return;
        }


        //检查是否存在上级pdkId目录,没有的话，则需要创建pdkID相关的目录
        String definitionPdkId = connectionDto.getDefinitionPdkId();
        if (StringUtils.isBlank(definitionPdkId)) {
            DataSourceDefinitionDto dataSourceType = definitionService.getByDataSourceType(connectionDto.getDatabase_type(), user);
            definitionPdkId = dataSourceType.getPdkId();
        }
        Criteria criteria = Criteria.where("item_type").is("default").and("value").is(definitionPdkId);
        Query query = new Query(criteria);
        MetadataDefinitionDto pdkIdDirectory = metadataDefinitionService.findOne(query, user);
        if (pdkIdDirectory == null) {
            pdkIdDirectory = addPdkId(connectionDto.getDefinitionPdkId(), user);
        }
        if (pdkIdDirectory == null) {
            return;
        }

        MetadataDefinitionDto metadataDefinitionDto = new MetadataDefinitionDto();
        metadataDefinitionDto.setValue(getConnectInfo(connectionDto));
        metadataDefinitionDto.setItemType(Lists.newArrayList("default"));
        metadataDefinitionDto.setReadOnly(true);
        metadataDefinitionDto.setParent_id(pdkIdDirectory.getId().toHexString());
        metadataDefinitionDto.setLinkId(connectionDto.getId().toHexString());
        metadataDefinitionService.save(metadataDefinitionDto, user);
    }

    @Override
    public void addConnections(UserDetail user) {
        Criteria criteria = Criteria.where("is_deleted").ne(true);
        List<DataSourceConnectionDto> allDto = dataSourceService.findAllDto(new Query(criteria), user);
        addConnections(allDto, user);
    }

    public void addConnections(List<DataSourceConnectionDto> connectionDtos, UserDetail user) {
        Set<String> pdkIds = new HashSet<>();
        for (DataSourceConnectionDto connectionDto : connectionDtos) {
            if (StringUtils.isNotBlank(connectionDto.getDefinitionPdkId())) {
                pdkIds.add(connectionDto.getDefinitionPdkId());
            } else {
                DataSourceDefinitionDto dataSourceType = definitionService.getByDataSourceType(connectionDto.getDatabase_type(), user);
                pdkIds.add(dataSourceType.getPdkId());
                connectionDto.setDefinitionPdkId(dataSourceType.getPdkId());
            }
        }
        //检查是否存在上级pdkId目录,没有的话，则需要创建pdkID相关的目录
        Criteria criteria = Criteria.where("item_type").is("default").and("value").in(pdkIds);
        Query query = new Query(criteria);
        List<MetadataDefinitionDto> metadataDefinitionDtos = metadataDefinitionService.findAllDto(query, user);

        Map<String, MetadataDefinitionDto> pdkIdMap = metadataDefinitionDtos.stream().collect(Collectors
                .toMap(MetadataDefinitionDto::getValue, s -> s, (s1, s2) -> s1));

        List<MetadataDefinitionDto> insertConnections = new ArrayList<>();
        for (DataSourceConnectionDto connectionDto : connectionDtos) {
            boolean exists = checkConnExists(connectionDto.getId().toHexString(), user);
            if (exists) {
                continue;
            }
            MetadataDefinitionDto metadataDefinitionDto = new MetadataDefinitionDto();
            metadataDefinitionDto.setValue(getConnectInfo(connectionDto));
            metadataDefinitionDto.setItemType(Lists.newArrayList("default"));
            metadataDefinitionDto.setReadOnly(true);
            metadataDefinitionDto.setLinkId(connectionDto.getId().toHexString());

            metadataDefinitionDto.setParent_id(pdkIdMap.get(connectionDto.getDefinitionPdkId()).getId().toHexString());
            insertConnections.add(metadataDefinitionDto);
        }
        metadataDefinitionService.save(insertConnections, user);
    }


    public void addPdkIds(UserDetail user) {
        //校验pdkId是否存在
        //查询得到所有的pdkId
        Criteria criteriaDefinition = Criteria.where("pdkType").is("pdk")
                .and("is_deleted").ne(true);
        Query queryDefinition = new Query(criteriaDefinition);
        List<DataSourceDefinitionDto> dataSourceDefinitionDtos = definitionService.findAllDto(queryDefinition, user);
        List<String> pdkIds = dataSourceDefinitionDtos.stream().map(DataSourceDefinitionDto::getPdkId).collect(Collectors.toList());

        //检查是否存在storage目录。如果不存在的话则需要创建storage的目录
        Criteria criteria = Criteria.where("item_type").is("storage");
        Query query = new Query(criteria);
        MetadataDefinitionDto storage = metadataDefinitionService.findOne(query, user);
        if (storage == null) {
            storage = addStorage(user);
        }

        Criteria in = Criteria.where("item_type").is("default").and("value").in(pdkIds);
        Query query1 = new Query(in);
        List<MetadataDefinitionDto> pdkDirectories = metadataDefinitionService.findAllDto(query1, user);
        Map<String, MetadataDefinitionDto> pdkDefinitionDtoMap = pdkDirectories.stream().collect(Collectors.toMap(MetadataDefinitionDto::getValue, s -> s, (s1, s2) -> s1));

        for (String pdkId : pdkIds) {
            if (pdkDefinitionDtoMap.get(pdkId) != null) {
                return;
            }

            MetadataDefinitionDto metadataDefinitionDto = new MetadataDefinitionDto();
            metadataDefinitionDto.setValue(pdkId);
            metadataDefinitionDto.setItemType(Lists.newArrayList("default"));
            metadataDefinitionDto.setReadOnly(true);
            metadataDefinitionDto.setParent_id(storage.getId().toHexString());

            metadataDefinitionService.save(metadataDefinitionDto, user);
        }
    }



    private MetadataDefinitionDto addPdkId(String pdkId, UserDetail user) {
        //校验pdkId是否存在
        boolean pdkIdExists = definitionService.checkExistsByPdkId(pdkId, user);
        if (!pdkIdExists) {
            return null;
        }


        //检查是否存在storage目录。如果不存在的话则需要创建storage的目录
        Criteria criteria = Criteria.where("item_type").is("storage");
        Query query = new Query(criteria);
        MetadataDefinitionDto storage = metadataDefinitionService.findOne(query, user);
        if (storage == null) {
            storage = addStorage(user);
        }

        MetadataDefinitionDto metadataDefinitionDto = new MetadataDefinitionDto();
        metadataDefinitionDto.setValue(pdkId);
        metadataDefinitionDto.setItemType(Lists.newArrayList("default"));
        metadataDefinitionDto.setReadOnly(true);
        metadataDefinitionDto.setParent_id(storage.getId().toHexString());

        return metadataDefinitionService.save(metadataDefinitionDto, user);
    }

    private MetadataDefinitionDto addStorage(UserDetail user){

        //检查是否存在storage目录。如果不存在的话则需要创建storage的目录
        Criteria criteria = Criteria.where("item_type").is("root");
        Query query = new Query(criteria);
        MetadataDefinitionDto root = metadataDefinitionService.findOne(query, user);
        if (root == null) {
            root = addDefaultRoot(user);
        }

        MetadataDefinitionDto metadataDefinitionDto = new MetadataDefinitionDto();
        metadataDefinitionDto.setValue("Storage");
        metadataDefinitionDto.setItemType(Lists.newArrayList("storage", "default"));
        metadataDefinitionDto.setDesc("storage");
        metadataDefinitionDto.setReadOnly(true);
        metadataDefinitionDto.setParent_id(root.getId().toHexString());

        return metadataDefinitionService.save(metadataDefinitionDto, user);
    }

    private MetadataDefinitionDto addDefaultRoot(UserDetail user) {
        MetadataDefinitionDto metadataDefinitionDto = new MetadataDefinitionDto();
        metadataDefinitionDto.setValue("Root");
        metadataDefinitionDto.setItemType(Lists.newArrayList("root", "default"));
        metadataDefinitionDto.setDesc("root");
        metadataDefinitionDto.setReadOnly(true);
        return metadataDefinitionService.save(metadataDefinitionDto, user);
    }


    private String getConnectInfo(DataSourceConnectionDto source) {
        String name = source.getName();
        StringBuilder ipAndPort = new StringBuilder(name);



        Object config = source.getConfig();
        Map config1 = (Map) config;
        if (source.getDatabase_type().toLowerCase(Locale.ROOT).contains("mongo")) {
            String uri1 = (String) config1.get("uri");
            if (StringUtils.isNotBlank(uri1)) {
                ConnectionString connectionString = new ConnectionString(uri1);
                List<String> hosts = connectionString.getHosts();
                if (CollectionUtils.isNotEmpty(hosts)) {
                    ipAndPort.append("(");
                    for (String host : hosts) {
                        ipAndPort.append(host).append(";");
                    }
                    ipAndPort = new StringBuilder(ipAndPort.substring(0, ipAndPort.length() -1));
                    ipAndPort.append(")");
                }
            }
        } else {
            Object host = config1.get("host");
            Object port = config1.get("port");
            if (host == null) {
                host = config1.get("mqHost");
            }
            if (port == null) {
                port = config1.get("mqPort");
            }
            if (StringUtils.isNotBlank((String)host)) {
                ipAndPort.append("(");
                ipAndPort.append(host.toString());
                if (port != null) {
                    ipAndPort.append(":").append(port);
                }
                ipAndPort.append(")");
            } else {
                ipAndPort = new StringBuilder();
            }
        }

        return ipAndPort.toString();
    }
}