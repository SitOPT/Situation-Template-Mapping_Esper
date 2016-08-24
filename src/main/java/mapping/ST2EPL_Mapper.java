package mapping;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import situation_template_cep.*;
import situation_template_cep.TConditionNode.CondValue;
import situation_template_cep.TConditionNode.CondVariable;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * This class maps the given Situation Template into two EPL Queries:
 * 
 * The first query matches the situation itself;
 * 
 * The second query matches when the situation occurred and then not anymore.
 * 
 */
public class ST2EPL_Mapper {

    private static final Log          log          = LogFactory.getLog(ST2EPL_Mapper.class);

    private TSituationTemplate        situationTemplate;

    private Map<String, List<Object>> childNodeIds = new HashMap<String, List<Object>>();   // <ParentID, children objects>

    private String                    query_situationOccurred;

    private String                    query_situationStopped;

    /**
     * @param situationTemplateString
     *            the xml situation template as string
     * @throws JAXBException
     *             if an error occurs while converting XML data into Java objects.
     */
    public ST2EPL_Mapper(String situationTemplateString) throws JAXBException {
        long startTime = System.currentTimeMillis();
        situationTemplate = unmarshal(new StreamSource(situationTemplateString));
        fillParentChildrenMap();
        startMapping();
        long endTime = System.currentTimeMillis();
        log.debug("Mapping time: " + (endTime - startTime) + " milliseconds");
    }

    public ST2EPL_Mapper(File situationTemplateFile) throws JAXBException {
        long startTime = System.currentTimeMillis();
        situationTemplate = unmarshal(new StreamSource(situationTemplateFile));
        fillParentChildrenMap();
        startMapping();
        long endTime = System.currentTimeMillis();
        log.debug("Mapping time: " + (endTime - startTime) + " milliseconds");
    }
    
    public static void main(String[] args) {
        boolean usedTempFile = args.length > 0;
        File situationTemplate;
        if (usedTempFile) {
            try {
                Path temp = Files.createTempFile("", "temp");
                String xml = args[0];
                xml = xml.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
                Files.write(temp, xml.getBytes());
                System.out.println(temp.toString());
                situationTemplate = new File(temp.toString());
            } catch (IOException e) {
                log.info("Could not create temporary file:\n" + e.getMessage());
                return;
            }
        } else {
            FileFilter filter = new FileNameExtensionFilter("XML File", "xml");
            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(new File(".\\src\\main\\resources\\situation_template"));
            chooser.setDialogTitle("Please choose situation template files");
            chooser.setMultiSelectionEnabled(false);
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setAcceptAllFileFilterUsed(false);
            chooser.addChoosableFileFilter(filter);
            //
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                situationTemplate = chooser.getSelectedFile();
            } else {
                log.info("No Selection ");
                return;
            }
        }
        try {
            ST2EPL_Mapper mapper = new ST2EPL_Mapper(situationTemplate);
            String situationTemplateID = mapper.getSituationTemplateID();
            log.debug(situationTemplateID);
            String mappedStatement_sitOccurred = mapper.getQuerySituationOccurred();
            log.debug(mappedStatement_sitOccurred);
            String mappedStatement_sitStopped = mapper.getQuerySituationStopped();
            log.debug(mappedStatement_sitStopped);
        } catch (JAXBException e) {
            e.printStackTrace();
        }
        if (usedTempFile) {
            situationTemplate.delete();
        }
    }

    public TSituationTemplate unmarshal(StreamSource streamSource) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(TSituationTemplate.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        JAXBElement<TSituationTemplate> root = jaxbUnmarshaller.unmarshal(streamSource, TSituationTemplate.class);
        return root.getValue();
    }

    /**
     * Fills parent-children map with corresponding relation. Each entry in map corresponds to one parent and a list of children.
     */
    private void fillParentChildrenMap() {
        // operation nodes
        for (TOperationNode operationNode : situationTemplate.getSituation().getOperationNode()) {
            createEdges(operationNode, operationNode.getParent());
        }

        // condition nodes
        for (TConditionNode conditionNode : situationTemplate.getSituation().getConditionNode()) {
            createEdges(conditionNode, conditionNode.getParent());
        }

        // context nodes
        for (TContextNode contextNode : situationTemplate.getSituation().getContextNode()) {
            createEdges(contextNode, contextNode.getParent());
        }

        // log.debug("Family edges: " + childNodeIds);
    }

    /**
     * Finds out parent-children relations and stores it into a hash map.
     */
    private void createEdges(Object child, List<TParent> parents) {
        for (TParent parent : parents) {
            Object parentObj = parent.getParentID();
            String parentID = null;

            if (parentObj instanceof TSituationNode) {
                parentID = ((TSituationNode) parentObj).getId();

            } else if (parentObj instanceof TOperationNode) {
                parentID = ((TOperationNode) parentObj).getId();

            } else if (parentObj instanceof TConditionNode) {
                parentID = ((TConditionNode) parentObj).getId();
            }

            if (parentID != null) {
                if (childNodeIds.containsKey(parentID)) {
                    childNodeIds.get(parentID).add(child);
                } else {
                    List<Object> kids = new ArrayList<Object>();
                    kids.add(child);
                    childNodeIds.put(parentID, kids);
                }
            }
        }
    }

    /**
     * Starts the mapping process.
     */
    private void startMapping() {

        String conditionExpression = "";
        String situationNodeID = situationTemplate.getSituation().getSituationNode().getId();

        // 1 - Builds an expression based only on condition node ID's.
        conditionExpression = buildConditionExpression(conditionExpression, situationNodeID);
        log.debug(conditionExpression);

        // 2 - creates both EPL Queries
        createEPLQueries(conditionExpression);

        log.debug(query_situationOccurred);
        log.debug(query_situationStopped);
    }

    /**
     * 
     * @return the situation template ID.
     */
    public String getSituationTemplateID() {
        return situationTemplate.getId();
    }

    /**
     * @return a string representing the EPL Query that matches when the situation occurred.
     */
    public String getQuerySituationOccurred() {
        return query_situationOccurred;
    }

    /**
     * @return a string representing the EPL Query that matches when the situation stopped occurring.
     */
    public String getQuerySituationStopped() {
        return query_situationStopped;
    }

    /**
     * Builds an expression based only on condition node IDs. These Id's will be replaced by the corresponding Esper conditions on a next step.
     * 
     * output example: (($id1 and $id2) or ($id3 and $id4))
     * 
     * @param statement
     *            the current built statement
     * @param parentId
     *            id of the current node to be processed
     * @return the expression for the situation template based on condition node ids
     */
    private String buildConditionExpression(String statement, String parentId) {

        if (childNodeIds.get(parentId) != null) {
            for (Object child : childNodeIds.get(parentId)) {

                if (child instanceof TOperationNode) {

                    TOperationNode opNode = (TOperationNode) child;

                    StringBuilder sb = new StringBuilder("(");
                    List<String> childrenList = new ArrayList<String>();

                    if (childNodeIds.get(opNode.getId()) != null) {
                        for (Object opNodechild : childNodeIds.get(opNode.getId())) {

                            if (opNodechild instanceof TOperationNode) {
                                childrenList.add(new String("$" + ((TOperationNode) opNodechild).getId()));

                            } else if (opNodechild instanceof TConditionNode) {
                                childrenList.add(new String("$" + ((TConditionNode) opNodechild).getId()));
                            }
                        }

                        sb.append(String.join(" $" + opNode.getType() + " ", childrenList));
                        sb.append(")");

                        if (statement.contains("$" + opNode.getId())) {
                            statement = statement.replace("$" + opNode.getId(), sb.toString());
                        } else {
                            statement = sb.toString();
                        }
                    }
                    // check kids
                    if (childNodeIds.get(opNode.getId()) != null) {
                        statement = buildConditionExpression(statement, opNode.getId()); // recursion
                    }

                } else if (situationTemplate.getSituation().getSituationNode().getId() == parentId && child instanceof TConditionNode) {
                    // if there is no operation node!
                    statement = new String("$" + ((TConditionNode) child).getId());
                }
            }
        }
        return statement;
    }

    /**
     * Build an Esper statement string by replacing the condition node Id's through corresponding Esper expressions
     * 
     * output example: select * from pattern [every ((DistanceMeterEvent(distance < 10))) ]
     * 
     * @param conditionExpression
     *            expression containing condition node Id's
     * 
     */
    private void createEPLQueries(String conditionExpression) {
        String statement = conditionExpression;
        String statement_situationStopped = conditionExpression; // this query means the situation occurred but not anymore

        // replaces logical operators
        statement = statement.replace(" $or ", " or ");
        statement = statement.replace(" $and ", " and ");
        statement_situationStopped = statement_situationStopped.replace(" $or ", " and ");
        statement_situationStopped = statement_situationStopped.replace(" $and ", " or ");

        // iterate through all condition nodes
        for (TConditionNode conditionNode : situationTemplate.getSituation().getConditionNode()) {

            // replaces each condition node id at the expression by corresponding esper clause
            if (statement.contains("$" + conditionNode.getId())) {

                // get context node, at least one context node must exist! more than one context node + CondValue -> aggregation
                List<Object> childrenNodes = childNodeIds.get(conditionNode.getId());
                if (childrenNodes != null && !childrenNodes.isEmpty()) {

                    String[] esperConditions = new String[2];

                    if ("condVariable".equals(conditionNode.getType())) {
                        // returns "(... epl condition based on pattern format ... )
                        esperConditions = buildEPLCondition_condVariable(conditionNode);

                    } else if ("condValue".equals(conditionNode.getType()) || conditionNode.getType() == null) {

                        // aggregates only if there is more than one context node to aggregate
                        if (conditionNode.getAggregation() != null && childrenNodes.size() > 1) {
                            // aggregation: avg, min, max
                            esperConditions = buildEPLCondition_condValue_aggregate(conditionNode, childrenNodes);

                        } else {
                            TContextNode contextNode = (TContextNode) childrenNodes.get(0);

                            // returns "(... epl condition based on pattern format ... )
                            esperConditions = buildEPLCondition_condValue(conditionNode, contextNode);

                        }
                    }

                    // replaces
                    statement = statement.replace("$" + conditionNode.getId(), esperConditions[0]);
                    statement_situationStopped = statement_situationStopped.replace("$" + conditionNode.getId(), esperConditions[1]);

                }
            }
        }

        query_situationOccurred = "select * from pattern [ " + statement + " ]";
        query_situationStopped = "select * from pattern [ " + statement_situationStopped + " ]";

    }

    /**
     * builds an EPL pattern definition for a condition node based on values (e.g. temperature > 90).
     * 
     * @param conditionNode
     * @param contextNode
     * @return
     */
    private String[] buildEPLCondition_condValue(TConditionNode conditionNode, TContextNode contextNode) {

        String[] patternDefinitions = new String[2];

        CondValue condValue = conditionNode.getCondValue();

        if (condValue == null || condValue.getValue() == null || condValue.getValue().size() < 1) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        StringBuilder sb_extended = new StringBuilder();
        BigInteger timeInterval = conditionNode.getTimeInterval();
        String streamName = contextNode.getId() + "_stream";

        if (timeInterval != null) { // time based condition
            // ( every contextNodeID_stream=DistanceMeterEvent(sensorID = 'test1', distance < 10)
            // -> every (timer:interval(5 sec) and not DistanceMeterEvent(sensorID = 'test1', distance > 10))
            // )
            sb.append("(");
            sb.append(" every " + streamName + "=" + buildValueBasedFilter(conditionNode, contextNode, false));
            sb.append(" -> (timer:interval (" + timeInterval + "msec) and not " + buildValueBasedFilter(conditionNode, contextNode, true) + ")");

        } else { // simple condition based on value
            // ( every contextNodeID_stream=DistanceMeterEvent(distance < 10) )

            sb.append("(");
            sb.append(" every " + streamName + "=" + buildValueBasedFilter(conditionNode, contextNode, false));
        }

        // extension for query to recognize when situation stopped occurring
        sb_extended.append(sb.toString());
        sb.append(")");

        // -> contextNodeID1_stream_sitStopped=DistanceMeterEvent (sensorID = 'test1', distance > 10)
        sb_extended.append(" -> " + streamName + "_sitStopped =" + buildValueBasedFilter(conditionNode, contextNode, true));
        sb_extended.append(")");

        patternDefinitions[0] = sb.toString();
        patternDefinitions[1] = sb_extended.toString();

        return patternDefinitions;
    }

    /**
     * builds an EPL pattern definition for a condition node based on values where the aggregated value from sensors shall be used.
     * 
     * (e.g. average (temperature) > 90).
     * 
     * @param conditionNode
     * @param contextNodes
     * @return
     */
    private String[] buildEPLCondition_condValue_aggregate(TConditionNode conditionNode, List<Object> contextNodes) {

        String[] patternDefinitions = new String[2];

        CondValue condValue = conditionNode.getCondValue();

        if (condValue == null || condValue.getValue() == null || condValue.getValue().size() < 1) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        StringBuilder sb_extended = new StringBuilder();
        BigInteger timeInterval = conditionNode.getTimeInterval();

        List<String> sensorIDs = new ArrayList<String>();
        List<String> variableNames = new ArrayList<String>();
        List<String> variableNames_sitStopped = new ArrayList<String>();

        List<String> filterAtoms = new ArrayList<String>();
        List<String> filterAtoms_sitStopped = new ArrayList<String>();

        String variableName = conditionNode.getMeasureName();

        String eventType = ((TContextNode) contextNodes.get(0)).getType(); // e.g. DistanceMeterEvent, ...

        for (Object child : contextNodes) {
            TContextNode contextNode = (TContextNode) child;
            String streamName = contextNode.getId() + "_stream";

            filterAtoms.add(streamName + "=" + eventType + "(sensorID = '" + contextNode.getId() + "')");
            filterAtoms_sitStopped.add(streamName + "_sitStopped=" + eventType + "(sensorID = '" + contextNode.getId() + "')");

            variableNames.add(streamName + "." + variableName);
            variableNames_sitStopped.add(streamName + "_sitStopped" + "." + variableName);

            sensorIDs.add(contextNode.getId());
            // TODO check if they are all the same event type
        }

        if (timeInterval != null) { // time based condition

            // ( every test1_stream=DistanceMeterEvent(sensorID = 'test1')->test2_stream=DistanceMeterEvent(sensorID = 'test2')
            // while ((test1_stream.distance + test2_stream.distance)/2 < 10)
            //
            // -> (timer:interval(5 sec)
            // and not DistanceMeterEvent(sensorID = 'test1', (distance + test2_stream.distance)/2 > 10)
            // and not DistanceMeterEvent(sensorID = 'test2', (test1_stream.distance + distance)/2 > 10)
            // )
            // )

            sb.append("(");
            sb.append(" every " + String.join(" -> ", filterAtoms) + " while (" + getAggregateFunction(conditionNode, variableNames, false) + ")");
            sb.append(" -> (timer:interval (" + timeInterval + " msec)");

            String aggregateFunction = getAggregateFunction(conditionNode, variableNames, true);

            for (int i = 0; i < sensorIDs.size(); i++) {
                String function = aggregateFunction.replace(sensorIDs.get(i) + "_stream.", "");

                sb.append(" and not " + eventType + "(sensorID = '" + sensorIDs.get(i) + "' , " + function + ")");
            }

            sb.append("))");

        } else { // simple condition based on value
            // ( every contextNodeID_stream=DistanceMeterEvent(sensorID = 'test1')->contextNodeID_stream=DistanceMeterEvent(sensorID = 'test2')
            // while ((contextNodeID_stream.distance + contextNodeID_stream)/2 < 10)
            // )
            sb.append("(");
            sb.append(" every " + String.join(" -> ", filterAtoms) + "  while (" + getAggregateFunction(conditionNode, variableNames, false) + ")");
            sb.append(")");
        }

        // extension for query to recognize when situation stopped occurring
        sb_extended.append("(");
        sb_extended.append(sb.toString());

        // -> contextNodeID_stream_sitStopped=DistanceMeterEvent(sensorID = 'test1')
        // -> contextNodeID_stream_sitStopped=DistanceMeterEvent(sensorID = 'test2')
        // while ((contextNodeID_stream_sitStopped.distance + contextNodeID_stream_sitStopped)/2 > 10)
        sb_extended.append(" -> " + String.join(" -> ", filterAtoms_sitStopped) + " while (" + getAggregateFunction(conditionNode, variableNames_sitStopped, true) + ")");
        sb_extended.append(")");

        patternDefinitions[0] = sb.toString();
        patternDefinitions[1] = sb_extended.toString();

        return patternDefinitions;
    }

    private String getAggregateFunction(TConditionNode conditionNode, List<String> variables, boolean negate) {
        // String stOperator, double compValue, double... values
        String function = "";
        String aggregationName = conditionNode.getAggregation();

        // arguments
        String operator = "'" + getEPLOperator(conditionNode.getOpType(), negate) + "'";
        int numCondValues = conditionNode.getCondValue().getValue().size();
        String args = "{" + String.join(", ", conditionNode.getCondValue().getValue()) + ", " + String.join(", ", variables) + "}";

        // evaluate aggregation type
        if ("avg".equalsIgnoreCase(aggregationName)) {
            function = Aggregation.class.getSimpleName() + ".check_avg(" + operator + ", " + numCondValues + ", " + args + ")";

        } else if ("min".equalsIgnoreCase(aggregationName)) {
            function = Aggregation.class.getSimpleName() + ".check_min(" + operator + ", " + numCondValues + ", " + args + ")";

        } else if ("max".equalsIgnoreCase(aggregationName)) {
            function = Aggregation.class.getSimpleName() + ".check_max(" + operator + ", " + numCondValues + ", " + args + ")";
        }

        return function;
    }

    /**
     * builds an EPL pattern definition for a condition node based on variables (e.g. sensor1.temperature > sensor2.temperature).
     * 
     * @param conditionNode
     * @return
     */
    private String[] buildEPLCondition_condVariable(TConditionNode conditionNode) {

        String[] patternDefinitions = new String[2];

        CondVariable condVariable = conditionNode.getCondVariable();

        if (condVariable == null || condVariable.getVariable() == null || condVariable.getVariable().size() != 2) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        StringBuilder sb_extended = new StringBuilder();
        BigInteger timeInterval = conditionNode.getTimeInterval();
        String variableName = conditionNode.getMeasureName();

        List<TVariableInput> variables = condVariable.getVariable();

        TContextNode contextNode1 = (TContextNode) variables.get(0).getContextNodeID();
        TContextNode contextNode2 = (TContextNode) variables.get(1).getContextNodeID();
        String streamName1 = contextNode1.getId() + "_stream";
        String streamName2 = contextNode2.getId() + "_stream";
        String operator = getEPLOperator(conditionNode.getOpType(), false);
        String inversedOperator = getEPLOperator(conditionNode.getOpType(), true);
        String eventType = contextNode1.getType(); // e.g. DistanceMeterEvent, ...

        String filter_sensorID_1 = "sensorID = '" + contextNode1.getId() + "'";
        String filter_sensorID_2 = "sensorID = '" + contextNode2.getId() + "'";

        // contextNodeID1_stream.distance > distance
        String filter_comparison = streamName1 + "." + variableName + " " + operator + " " + variableName;
        // contextNodeID1_stream.distance < distance
        String filter_comparison_inversed = streamName1 + "." + variableName + " " + inversedOperator + " " + variableName;
        // contextNodeID1_stream.distance > distance
        String filter_notEqual = streamName1 + "." + variableName + " != " + variableName;

        if (timeInterval != null) { // time based condition

            // ( every contextNodeID1_stream=DistanceMeterEvent (sensorID = 'test1')
            // -> (contextNodeID2_stream=DistanceMeterEvent (sensorID = 'test2', contextNodeID1_stream.distance > distance )
            // and not DistanceMeterEvent (sensorID = 'test1'))
            //
            // -> ( timer:interval(x sec) and not DistanceMeterEvent(sensorID = 'test2', contextNodeID1_stream.distance < distance)
            // and not DistanceMeterEvent(sensorID = 'test1', contextNodeID1_stream.distance != distance, distance < contextNodeID2_stream.distance))
            // )
            sb.append("(");
            sb.append(" every " + streamName1 + "=" + eventType + "(" + filter_sensorID_1 + ")");

            sb.append(" -> (" + streamName2 + "=" + eventType + "(" + filter_sensorID_2 + ", " + filter_comparison + ")");
            sb.append(" and not " + eventType + "(" + filter_sensorID_1 + ")) ");

            sb.append(" -> (timer:interval (" + timeInterval + " msec) and not " + eventType + "(" + filter_sensorID_2 + ", " + filter_comparison_inversed + ") ");
            sb.append(" and not " + eventType + "(" + filter_sensorID_1 + ", " + filter_notEqual + ", " + variableName + " " + inversedOperator + " " + streamName2 + "."
                    + variableName + "))");

        } else { // simple condition based on variables

            // ( every contextNodeID1_stream=DistanceMeterEvent (sensorID = 'test1')
            // -> ( contextNodeID2_stream=DistanceMeterEvent (sensorID = 'test2', contextNodeID1_stream.distance > distance )
            // and not DistanceMeterEvent (sensorID = 'test1'))
            // )
            sb.append("(");
            sb.append(" every " + streamName1 + "=" + eventType + "(" + filter_sensorID_1 + ")");

            sb.append(" -> (" + streamName2 + "=" + eventType + "(" + filter_sensorID_2 + ", " + filter_comparison + ")");
            sb.append(" and not " + eventType + "(" + filter_sensorID_1 + ")) ");

            // TODO: check if this is a better pattern and if it works with time interval
            // String eplStatement6 =
            // "select * from pattern [every ( a=DistanceMeterEvent (sensorID = 'A0') and b=DistanceMeterEvent (sensorID = 'A1') )"
            // + " while (a.distance > b.distance) ]";
        }

        // extension for query to recognize when situation stopped occurring
        sb_extended.append(sb.toString());
        sb.append(")");

        // -> contextNodeID1_stream_sitStopped=DistanceMeterEvent (sensorID = 'test1')
        // -> (contextNodeID2_stream_sitStopped=DistanceMeterEvent (sensorID = 'test2', contextNodeID1_stream_sitStopped.distance < distance )
        // and not DistanceMeterEvent (sensorID = 'test1'))
        String filter_comparison_inversed_sitStopped = streamName1 + "_sitStopped." + variableName + " " + inversedOperator + " " + variableName;

        sb_extended.append(" -> " + streamName1 + "_sitStopped =" + eventType + "(" + filter_sensorID_1 + ")");
        sb_extended.append(" -> (" + streamName2 + "_sitStopped =" + eventType + "(" + filter_sensorID_2 + ", " + filter_comparison_inversed_sitStopped + ")");
        sb_extended.append(" and not " + eventType + "(" + filter_sensorID_1 + ")) ");
        sb_extended.append(")");

        patternDefinitions[0] = sb.toString();
        patternDefinitions[1] = sb_extended.toString();
        return patternDefinitions;
    }

    /**
     * builds a pattern filter for a condition node based on values.
     * 
     * Output example: DistanceMeterEvent(sensorID= 'A0', distance < 10)
     * 
     * @param conditionNode
     * @param contextNode
     * @param negate
     * @return
     */
    private String buildValueBasedFilter(TConditionNode conditionNode, TContextNode contextNode, boolean negate) {
        String eventType = contextNode.getType(); // e.g. DistanceMeterEvent, ...
        String variableName = conditionNode.getMeasureName();
        String operator = conditionNode.getOpType();
        List<String> values = conditionNode.getCondValue().getValue();

        StringBuilder sb = new StringBuilder(eventType);
        sb.append("(sensorID='" + contextNode.getId() + "', ");

        if ("between".equals(operator)) {
            sb.append(variableName);
            sb.append(" between ");
            sb.append(values.get(0));
            sb.append(" and ");
            sb.append(values.get(1));
        } else {
            sb.append(variableName + " " + getEPLOperator(operator, negate) + " " + values.get(0));
        }

        sb.append(")");
        return sb.toString();
    }

    private String getEPLOperator(String stOperator, boolean negate) {
        // situation template operator = lowerThan|greaterThan|equals|notEquals|between
        String operator = null;
        if ("lowerThan".equals(stOperator)) {
            if (negate == true) {
                operator = ">";
            } else {
                operator = "<";
            }

        } else if ("greaterThan".equals(stOperator)) {
            if (negate == true) {
                operator = "<";
            } else {
                operator = ">";
            }

        } else if ("equals".equals(stOperator)) {
            if (negate == true) {
                operator = "!=";
            } else {
                operator = "=";
            }

        } else if ("notEquals".equals(stOperator)) {
            if (negate == true) {
                operator = "=";
            } else {
                operator = "!=";
            }

        } else if ("between".equals(stOperator)) {
            operator = "between";
        }
        return operator;
    }

    private File[] loadXMLDir() {
        FileFilter filter = new FileNameExtensionFilter("XML File", "xml");
        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new java.io.File(".\\src\\main\\resources\\situation_template\\xml"));
        chooser.setDialogTitle("Please choose situation template files");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.addChoosableFileFilter(filter);
        //
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFiles();
        } else {
            log.info("No Selection ");
            return null;
        }

    }
}
