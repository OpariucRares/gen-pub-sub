package storm;

import models.publication.Publication;
import models.publication.PublicationField;
import models.subscription.Subscription;
import models.subscription.SubscriptionField;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.*;

public class BrokerBolt extends BaseRichBolt {
    private OutputCollector collector;
//    private List<Subscription> subscriptions;
//    private Map<String, List<Map<String, Map<Object, String>>>> subscriptionMap;
    private final Map<String, List<String>> subscriptionMap;

    public BrokerBolt() {
//        this.subscriptions = new ArrayList<>();
//        this.subscriptionMap = new HashMap<>();
        this.subscriptionMap = Collections.synchronizedMap(new HashMap<>());
    }

    @Override
    public void prepare(Map<String, Object> topoConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple tuple) {
        String streamId = tuple.getSourceStreamId();

        if ("subscription-stream".equals(streamId)) {
            System.out.println("subscriber...");
            String subscriberId = tuple.getStringByField("subscriberId");
            String subscription = tuple.getStringByField("subscription");

            synchronized (subscriptionMap) {
                subscriptionMap.computeIfAbsent(subscriberId, k -> new ArrayList<>()).add(subscription);
                System.out.println("Added to subMap! Current map size: " + subscriptionMap.size());
                System.out.println("Current subscriptionMap: " + subscriptionMap);
            }

//            TODO: Identity routing (this is simple routing)
            collector.emit("subscription-stream", new Values(subscriberId, subscription));

//            TODO: pre-process subscriptions (now String) so it is easier to match
//            Map<String, String> company = (Map<String, String>) tuple.getValueByField("company");
//            Map<Double, String> value = (Map<Double, String>) tuple.getValueByField("value");
//            Map<Double, String> drop = (Map<Double, String>) tuple.getValueByField("drop");
//            Map<Double, String> variation = (Map<Double, String>) tuple.getValueByField("variation");
//            Map<Date, String> date = (Map<Date, String>) tuple.getValueByField("date");
//
//            // Create a list of maps representing the subscription fields
//            Map<String, Map<Object, String>> subscriptionFields = new HashMap<>();
//            subscriptionFields.put("company", (Map<Object, String>) (Map<?, ?>) company);
//            subscriptionFields.put("value", (Map<Object, String>) (Map<?, ?>) value);
//            subscriptionFields.put("drop", (Map<Object, String>) (Map<?, ?>) drop);
//            subscriptionFields.put("variation", (Map<Object, String>) (Map<?, ?>) variation);
//            subscriptionFields.put("date", (Map<Object, String>) (Map<?, ?>) date);

//            synchronized (subscriptionMap) {
//                subscriptionMap.computeIfAbsent(subscriberId, k -> new ArrayList<>()).add(subscriptionFields);
//                System.out.println("Added to subMap! Current map size: " + subscriptionMap.size());
//                System.out.println("Current subscriptionMap: " + subscriptionMap);
//            }
//            Subscription subscription = (Subscription) tuple.getValueByField("subscription");

        } else if ("notification-stream".equals(streamId) || "default".equals(streamId)) {
            System.out.println("Processing publication...");
            String company = tuple.getStringByField("company");
            double value = tuple.getDoubleByField("value");
            double drop = tuple.getDoubleByField("drop");
            double variation = tuple.getDoubleByField("variation");
            Date date = (Date) tuple.getValueByField("date");

            Publication publication = new Publication();
            publication.addField(new PublicationField("company", company));
            publication.addField(new PublicationField("value", value));
            publication.addField(new PublicationField("drop", drop));
            publication.addField(new PublicationField("variation", variation));
            publication.addField(new PublicationField("date", date));

//            TODO:Matching algorithm
            System.out.println("SubscriptionMap before processing publication: " + subscriptionMap);
//            synchronized (subscriptionMap) {
//                System.out.println("SubscriptionMap before processing publication: " + subscriptionMap);
//                for (Map.Entry<String, List<Map<String, Map<Object, String>>>> entry : subscriptionMap.entrySet()) {
//                    String subscriberId = entry.getKey();
//                    List<Map<String, Map<Object, String>>> subscriptions = entry.getValue();
//                    for (Map<String, Map<Object, String>> subscription : subscriptions) {
//                        if (matches(subscription, publication)) {
//                            collector.emit("notification-stream", new Values(subscriberId, publication));
//                        }
//                    }
//                }
//            }
        }

    }

    // TODO: fix
    private boolean matches(Map<String, Map<Object, String>> subscription, Publication publication) {
        for (PublicationField pubField : publication.getFields()) {
            String pubFieldName = pubField.getFieldName();
            Object pubFieldValue = pubField.getValue();
            if (subscription.containsKey(pubFieldName)) {
                Map<Object, String> subFieldMap = subscription.get(pubFieldName);
                if (subFieldMap.containsKey(pubFieldValue)) {
                    // Assuming subFieldMap value as the operator for simplicity, adjust as needed
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream("notification-stream", new Fields("subscriberId", "publication"));
        declarer.declareStream("subscription-stream", new Fields("subscriberId", "subscription"));
    }
}