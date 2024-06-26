import models.publication.Publication;
import models.publication.PublicationField;
import models.publication.PublicationOuterClass;
import models.subscription.Subscription;
import models.subscription.SubscriptionField;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.KillOptions;
import org.apache.storm.topology.TopologyBuilder;
import models.publication.PublicationGenerator;
import models.subscription.SubscriptionGenerator;
import org.apache.storm.tuple.Fields;
import storm.BrokerBolt;
import storm.PublisherSpout;
import storm.SecondaryBolt;
import storm.SubscriberBolt;
import util.Constants;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws Exception {
        Constants constants = new Constants();

        SubscriptionGenerator subscriptionGenerator = new SubscriptionGenerator();

        var subscriptions1 = subscriptionGenerator.generateSubscriptions(50, constants.fieldFreq, constants.eqFreq);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("results/subscriptions1.txt"))) {
            for (var subscription : subscriptions1) {
                writer.write(subscription.toString());
                writer.newLine();
            }
            System.out.println("Subscriptions written to subscriptions.txt");
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }

        var subscriptions2 = subscriptionGenerator.generateSubscriptions(50, constants.fieldFreq, constants.eqFreq);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("results/subscriptions2.txt"))) {
            for (var subscription : subscriptions2) {
                writer.write(subscription.toString());
                writer.newLine();
            }
            System.out.println("Subscriptions written to subscriptions.txt");
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }

        PublicationGenerator publicationGenerator = new PublicationGenerator();
        var publications = publicationGenerator.generatePublications(2000, constants.pubFieldFreq);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("results/publications.txt"))) {
            for (var publication : publications) {
                StringBuilder sb = new StringBuilder("{");
                for (PublicationOuterClass.PublicationField field : publication.getFieldsList()) {
                    switch (field.getValueCase()) {
                        case COMPANYFIELD:
                            sb.append("(company, ").append(field.getCompanyField()).append("); ");
                            break;
                        case VALUEFIELD:
                            sb.append("(value, ").append(field.getValueField()).append("); ");
                            break;
                        case DROPFIELD:
                            sb.append("(drop, ").append(field.getDropField()).append("); ");
                            break;
                        case VARIATIONFIELD:
                            sb.append("(variation, ").append(field.getVariationField()).append("); ");
                            break;
                        case DATEFIELD:
                            sb.append("(date, ").append(field.getDateField()).append("); ");
                            break;
                        case VALUE_NOT_SET:
                            sb.append("(BROKEN, ").append("); ");
                            break;
                    }
                }
                sb.delete(sb.length() - 2, sb.length());
                sb.append("}");
                writer.write(sb.toString());
                writer.newLine();
            }
            System.out.println("Publications written to publications.txt");
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }

        PublisherSpout publisherSpout1 = new PublisherSpout(publications);
//        PublisherSpout publisherSpout2 = new PublisherSpout(publications);
        BrokerBolt brokerBoltDecode = new BrokerBolt("broker-decode");
        BrokerBolt brokerBolt1 = new BrokerBolt("broker1");
        SecondaryBolt brokerBolt2Secondary = new SecondaryBolt("broker2-secondary");
        BrokerBolt brokerBolt2 = new BrokerBolt("broker2");
        BrokerBolt brokerBolt3 = new BrokerBolt("broker3");
        SubscriberBolt subscriberBolt1 = new SubscriberBolt("subscriber1", subscriptions1);
        SubscriberBolt subscriberBolt2 = new SubscriberBolt("subscriber2", subscriptions2);
//        SubscriberBolt subscriberBolt3 = new SubscriberBolt("subscriber-789", subscriptions);

        TopologyBuilder builder = new TopologyBuilder();

        // Adăugarea PublisherSpout la topologie
        builder.setSpout("publisher-spout-1", publisherSpout1, 1);
        // builder.setSpout("publisher-spout2", publisherSpout2, 2);

        // Adăugarea BrokerBolt la topologie
        builder.setBolt("broker-bolt-decode", brokerBoltDecode, 1)
                        .shuffleGrouping("publisher-spout-1");
                        // .fieldsGrouping("broker-bolt-1", "decoded-stream", new Fields("company", "value", "drop", "variation", "date"));

        builder.setBolt("broker-bolt-1", brokerBolt1, 1)
                .shuffleGrouping("broker-bolt-decode", "decoded-stream")
                .fieldsGrouping("broker-bolt-2", "subscription-stream", new Fields("subscriberId"))
                .fieldsGrouping("broker-bolt-3", "subscription-stream", new Fields("subscriberId"));

        builder.setBolt("broker-bolt-2", brokerBolt2, 1)
                .shuffleGrouping("broker-bolt-1", "notification-stream")
                .fieldsGrouping("subscriber-bolt-1", "subscription-stream", new Fields("subscriberId"))
                .fieldsGrouping("broker-bolt-2-secondary", "heartbeat-stream", new Fields("heartbeat"));

        builder.setBolt("broker-bolt-2-secondary", brokerBolt2Secondary, 1)
                .shuffleGrouping("broker-bolt-2", "heartbeat-stream")
                .shuffleGrouping("broker-bolt-1", "notification-stream")
                .fieldsGrouping("subscriber-bolt-1", "subscription-stream", new Fields("subscriberId"));

        builder.setBolt("broker-bolt-3", brokerBolt3, 1)
                .shuffleGrouping("broker-bolt-1", "notification-stream")
                .fieldsGrouping("subscriber-bolt-2", "subscription-stream", new Fields("subscriberId"));

        // Adăugarea SubscriberBolt la topologie
        builder.setBolt("subscriber-bolt-1", subscriberBolt1, 1)
                .shuffleGrouping("broker-bolt-2", "notification-stream");

        builder.setBolt("subscriber-bolt-2", subscriberBolt2, 1)
                .shuffleGrouping("broker-bolt-3", "notification-stream");

        // Config
        Config config = new Config();
        config.setDebug(true);

        config.setNumWorkers(3);

        config.put(Config.STORM_ZOOKEEPER_SERVERS, Arrays.asList("localhost"));
        config.put(Config.STORM_ZOOKEEPER_PORT, 2181);

        config.registerSerialization(java.util.Date.class);
        config.registerSerialization(Subscription.class);
        config.registerSerialization(Publication.class);
        config.registerSerialization(PublicationField.class);
        config.registerSerialization(SubscriptionField.class);
        config.registerSerialization(Collections.unmodifiableList(Collections.emptyList()).getClass());
        config.registerSerialization(Collections.synchronizedList(Collections.emptyList()).getClass());
        config.registerSerialization(Collections.unmodifiableMap(Collections.emptyMap()).getClass());
        config.registerSerialization(Collections.synchronizedMap(Collections.emptyMap()).getClass());


        //config.registerSerialization(models.subscription.Subscription.class);
        //config.registerSerialization(java.util.Collections.class);

        if (args.length == 0) {
            // Run the topology in a local cluster
            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology("word-count-topology-one", config, builder.createTopology());

            Thread.sleep(10000);
            System.err.println("Simulated failure on broker 2");

            KillOptions killOptions = new KillOptions();
            killOptions.set_wait_secs(0);
            cluster.killTopologyWithOpts("word-count-topology-one", killOptions);
            // Sleep for a short period to ensure the topology is killed
            try {
                Thread.sleep(5000); // Wait for 5 seconds to ensure the topology is killed
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            TopologyBuilder newBuilder = new TopologyBuilder();

            // Adăugarea PublisherSpout la topologie
            newBuilder.setSpout("Fpublisher-spout-1", publisherSpout1, 1);
            // builder.setSpout("publisher-spout2", publisherSpout2, 2);

            // Adăugarea BrokerBolt la topologie
            newBuilder.setBolt("Fbroker-bolt-decode", brokerBoltDecode, 1)
                    .shuffleGrouping("Fpublisher-spout-1");
            // .fieldsGrouping("broker-bolt-1", "decoded-stream", new Fields("company", "value", "drop", "variation", "date"));

            newBuilder.setBolt("Fbroker-bolt-1", brokerBolt1, 1)
                    .shuffleGrouping("Fbroker-bolt-decode", "decoded-stream")
                    .fieldsGrouping("Fbroker-bolt-2", "subscription-stream", new Fields("subscriberId"))
                    .fieldsGrouping("Fbroker-bolt-3", "subscription-stream", new Fields("subscriberId"));

            newBuilder.setBolt("Fbroker-bolt-2", brokerBolt2, 1);
//                    .shuffleGrouping("broker-bolt-1", "notification-stream")
//                    .fieldsGrouping("subscriber-bolt-1", "subscription-stream", new Fields("subscriberId"))
//                    .fieldsGrouping("broker-bolt-2-secondary", "heartbeat-stream", new Fields("heartbeat"));

            newBuilder.setBolt("Fbroker-bolt-2-secondary", brokerBolt2Secondary, 1)
                    //.shuffleGrouping("Fbroker-bolt-2", "heartbeat-stream")
                    .shuffleGrouping("Fbroker-bolt-1", "notification-stream")
                    .fieldsGrouping("Fsubscriber-bolt-1", "subscription-stream", new Fields("subscriberId"));

            newBuilder.setBolt("Fbroker-bolt-3", brokerBolt3, 1)
                    .shuffleGrouping("Fbroker-bolt-1", "notification-stream")
                    .fieldsGrouping("Fsubscriber-bolt-2", "subscription-stream", new Fields("subscriberId"));

            // Adăugarea SubscriberBolt la topologie
            newBuilder.setBolt("Fsubscriber-bolt-1", subscriberBolt1, 1)
                    .shuffleGrouping("Fbroker-bolt-2", "notification-stream");

            newBuilder.setBolt("Fsubscriber-bolt-2", subscriberBolt2, 1)
                    .shuffleGrouping("Fbroker-bolt-3", "notification-stream");

            cluster.submitTopology("word-count-topology-one", config, newBuilder.createTopology());

            // brokerBolt1.simulateFailure();
            // System.err.println("Simulated failure on Primary: " + brokerBolt1.getBrokerId());

//            Thread.sleep(1000);
//            System.err.println("Is Primary Active for Primary: " + brokerBolt1.getIsPrimaryActive());
//            System.err.println("Is Primary Active for Secondary: " + brokerBolt1Secondary.getIsPrimaryActive());

            // Keep the topology running for some time (e.g., 60 seconds) for demonstration purposes
//            Thread.sleep(60000 * 3); // multiplied by the number of minutes wanted
            Thread.sleep(60000 * 3);

            // Shutdown the local cluster
            cluster.shutdown();
        } else {
            // Submit the topology to the Storm cluster
            StormSubmitter.submitTopology(args[0], config, builder.createTopology());
        }

        int publicationsReceived = readPublicationsReceived("results/stats/broker1.txt", false);
        int publicationsSent = readPublicationsReceived("results/publisher.txt", false);

        int matchedPubsSubOne = readPublicationsReceived("results/stats/subscriber1.txt", false);
        int latencySubOne = readPublicationsReceived("results/stats/subscriber1.txt", true);
        int matchedPubsSubTwo = readPublicationsReceived("results/stats/subscriber2.txt", false);
        int latencySubTwo = readPublicationsReceived("results/stats/subscriber2.txt", true);

        if (publicationsReceived == -1 || matchedPubsSubOne == -1 || matchedPubsSubTwo == -1) {
            System.err.println("Error reading publications received from files.");
            return;
        }

        double matchRate = (double) (matchedPubsSubOne + matchedPubsSubTwo) / (publicationsReceived * 2);
        double matchRatePercentage = matchRate * 100;

//        writeStatistics("results/stats/company/company_25.txt", publicationsReceived, matchedPubsSubOne, matchedPubsSubTwo,
//                latencySubOne, latencySubTwo, matchRatePercentage, publicationsSent);
        writeStatistics("results/stats/company/company_100.txt", publicationsReceived, matchedPubsSubOne, matchedPubsSubTwo,
                latencySubOne, latencySubTwo, matchRatePercentage, publicationsSent);
//        writeStatistics("results/stats/date/date_25.txt", publicationsReceived, matchedPubsSubOne, matchedPubsSubTwo,
//                latencySubOne, latencySubTwo, matchRatePercentage, publicationsSent);
//        writeStatistics("results/stats/date/date_100.txt", publicationsReceived, matchedPubsSubOne, matchedPubsSubTwo,
//                latencySubOne, latencySubTwo, matchRatePercentage, publicationsSent);
//        writeStatistics("results/stats/drop/drop_25.txt", publicationsReceived, matchedPubsSubOne, matchedPubsSubTwo,
//                latencySubOne, latencySubTwo, matchRatePercentage, publicationsSent);
//        writeStatistics("results/stats/drop/drop_100.txt", publicationsReceived, matchedPubsSubOne, matchedPubsSubTwo,
//                latencySubOne, latencySubTwo, matchRatePercentage, publicationsSent);
//        writeStatistics("results/stats/value/value_25.txt", publicationsReceived, matchedPubsSubOne, matchedPubsSubTwo,
//                latencySubOne, latencySubTwo, matchRatePercentage, publicationsSent);
//        writeStatistics("results/stats/value/value_100.txt", publicationsReceived, matchedPubsSubOne, matchedPubsSubTwo,
//                latencySubOne, latencySubTwo, matchRatePercentage, publicationsSent);
//        writeStatistics("results/stats/variation/variation_25.txt", publicationsReceived, matchedPubsSubOne, matchedPubsSubTwo,
//                latencySubOne, latencySubTwo, matchRatePercentage, publicationsSent);
//        writeStatistics("results/stats/variation/variation_100.txt", publicationsReceived, matchedPubsSubOne, matchedPubsSubTwo,
//                latencySubOne, latencySubTwo, matchRatePercentage, publicationsSent);

    }
    private static void writeStatistics(String filePath, int publicationsReceived, int matchedPubsSubOne,
                                        int matchedPubsSubTwo, int latencySub1, int latencySub2, double matchRatePercentage,
                                        int publicationsSent) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            writer.write("Publications sent: " + publicationsSent + "\n");
            writer.write("Publications received: " + publicationsReceived + "\n");
            writer.write("Matched publications subscriber 1: " + matchedPubsSubOne + "\n");
            writer.write("Latency subscriber 1: " + latencySub1 + " ms\n");
            writer.write("Matched publications subscriber 2: " + matchedPubsSubTwo + "\n");
            writer.write("Latency subscriber 2: " + latencySub2 + " ms\n");
            writer.write("Match rate: " + String.format("%.2f", matchRatePercentage) + "%\n");
            Map.Entry<String, Double> entry = new Constants().eqFreq.entrySet().iterator().next();
            String key = entry.getKey();
            Double value = entry.getValue();
            writer.write("Important key " + key + "\n");
            writer.write("Equal percent " + value + "\n");

        } catch (IOException e) {
            System.err.println("Error writing to file: " + filePath);
            e.printStackTrace();
        }
    }
    private static int readPublicationsReceived(String filePath, boolean ignoreFirstLine) {
        int publicationsReceived = -1;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if ((line.startsWith("Publications received:") || line.startsWith("Publications sent:")) && !ignoreFirstLine) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        String publicationsReceivedStr = parts[1].trim();
                        publicationsReceived = Integer.parseInt(publicationsReceivedStr);
                    } else {
                        System.err.println("Line format is incorrect in file: " + filePath);
                    }
                    break; // Exit after finding the relevant line
                }
                else if (line.startsWith("Mean latency:")) {
                    String[] parts = line.split(":");
                    if (parts.length == 2) {
                        String valueStr = parts[1].trim().split(" ")[0];
                        publicationsReceived = Integer.parseInt(valueStr);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + filePath);
            e.printStackTrace();
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse the number of publications received in file: " + filePath);
            e.printStackTrace();
        }
        return publicationsReceived;
    }

}