package io.tapdata.autoinspect.runner;

import com.tapdata.constant.ConnectionUtil;
import com.tapdata.constant.ConnectorConstant;
import com.tapdata.entity.Connections;
import com.tapdata.entity.DatabaseTypeEnum;
import com.tapdata.mongo.ClientMongoOperator;
import com.tapdata.tm.autoinspect.compare.IAutoCompare;
import com.tapdata.tm.autoinspect.connector.IPdkConnector;
import com.tapdata.tm.autoinspect.constants.TaskType;
import com.tapdata.tm.autoinspect.entity.AutoInspectProgress;
import com.tapdata.tm.autoinspect.entity.CompareTableItem;
import com.tapdata.tm.commons.dag.nodes.AutoInspectNode;
import com.tapdata.tm.commons.dag.vo.SyncObjects;
import io.tapdata.autoinspect.AutoInspectRunner;
import io.tapdata.autoinspect.compare.AutoPdkCompare;
import io.tapdata.autoinspect.connector.PdkConnector;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.observable.logging.ObsLogger;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.*;

/**
 * @author <a href="mailto:harsen_lin@163.com">Harsen</a>
 * @version v1.0 2022/8/18 14:57 Create
 */
public abstract class PdkAutoInspectRunner extends AutoInspectRunner<IPdkConnector, IPdkConnector, IAutoCompare> {
    private final ClientMongoOperator clientMongoOperator;
    private final AutoInspectNode node;
    private final Map<String, Connections> connectionMap;

    public PdkAutoInspectRunner(ObsLogger userLogger, String taskId, TaskType taskType, AutoInspectProgress progress, AutoInspectNode node, ClientMongoOperator clientMongoOperator) {
        super(userLogger, taskId, taskType, progress);
        this.clientMongoOperator = clientMongoOperator;
        this.node = node;
        this.connectionMap = getConnectionsByIds(new HashSet<>(Arrays.asList(
                node.getFromNode().getConnectionId(),
                node.getToNode().getConnectionId()
        )));
    }

    @Override
    protected IPdkConnector openSourceConnector() throws Exception {
        Connections conn = connectionMap.computeIfAbsent(node.getFromNode().getConnectionId(), s -> {
            throw new RuntimeException("create node failed because source connection not found: " + s);
        });
        DatabaseTypeEnum.DatabaseType databaseType = ConnectionUtil.getDatabaseType(clientMongoOperator, conn.getPdkHash());
        return new PdkConnector(clientMongoOperator, node.getFromNode(), conn, databaseType, this::isRunning);
    }

    @Override
    protected IPdkConnector openTargetConnector() throws Exception {
        Connections conn = connectionMap.computeIfAbsent(node.getToNode().getConnectionId(), s -> {
            throw new RuntimeException("create node failed because target connection not found: " + s);
        });
        DatabaseTypeEnum.DatabaseType databaseType = ConnectionUtil.getDatabaseType(clientMongoOperator, conn.getPdkHash());
        return new PdkConnector(clientMongoOperator, node.getToNode(), conn, databaseType, this::isRunning);
    }

    @Override
    protected IAutoCompare openAutoCompare(IPdkConnector sourceConnector, IPdkConnector targetConnector) throws Exception {
        return new AutoPdkCompare(clientMongoOperator, sourceConnector, targetConnector);
    }

    protected void init(AutoInspectProgress progress, IPdkConnector sourceConnector, IPdkConnector targetConnector) throws Exception {
        TapTable tapTable;
        for (SyncObjects syncObjects : node.getSyncObjects()) {
            if (!"table".equals(syncObjects.getType())) continue;

            for (String tableName : syncObjects.getObjectNames()) {
                if (!isRunning()) return;

                tapTable = sourceConnector.getTapTable(tableName);
                if (null == tapTable.primaryKeys() || tapTable.primaryKeys().isEmpty()) {
                    userLogger.warn("Ignore non primary key table: {}", tableName);
                    progress.addTableIgnore(1);
                    continue;
                }
                tapTable = targetConnector.getTapTable(tableName);
                if (null == tapTable.primaryKeys() || tapTable.primaryKeys().isEmpty()) {
                    userLogger.warn("Ignore non primary key table: {}", tableName);
                    progress.addTableIgnore(1);
                    continue;
                }
                progress.addTableCounts(1);
                progress.addTableItem(new CompareTableItem(tableName));
            }
        }
    }

    private Map<String, Connections> getConnectionsByIds(Set<String> ids) {
        Query query = Query.query(Criteria.where("_id").in(ids));
        query.fields().exclude("response_body").exclude("schema");
        List<Connections> connections = clientMongoOperator.find(query, ConnectorConstant.CONNECTION_COLLECTION + "/listAll", Connections.class);

        Map<String, Connections> retMap = new HashMap<>();
        if (null != connections) {
            connections.forEach(conn -> {
                retMap.put(conn.getId(), conn);
            });
        }
        return retMap;
    }

    @Override
    protected void updateProgress(AutoInspectProgress progress) {
        Query query = Query.query(Criteria.where("_id").is(new ObjectId(taskId)));
        Update update = Update.update("attrs.autoInspectProgress", progress);
        clientMongoOperator.update(query, update, ConnectorConstant.TASK_COLLECTION);
    }
}