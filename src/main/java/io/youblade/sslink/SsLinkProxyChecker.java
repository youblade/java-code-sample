package io.youblade.sslink;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.ToStringBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * TODO
 *
 * @author youblade
 * @created 2016-11-26 17:30
 * @since v1.4
 */
public class SsLinkProxyChecker {

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {

        /**
         *
         */
        File ipListFile = FileUtils.getFile("src/main/resources/sslink-ip.html");

        List<String> lines = FileUtils.readLines(ipListFile);
        Set<String> ipSet = lines.stream().map(l -> StringUtils.substringBetween(l, "value=", ">"))
            .map(l -> l.substring(1, l.lastIndexOf("\""))).collect(toSet());

        // 提取key和value的函数
        Function<String, String> ipAsKey = line -> line.substring(line.indexOf("\"") + 1, line.indexOf(">") - 1);
        Function<String, String> titleAsValue = l -> l.substring(l.indexOf(">"), l.lastIndexOf(", "));

        Map<String, String> ipTitleMap = lines.stream().collect(toMap(ipAsKey, titleAsValue));
        System.out.println(ipTitleMap);

        /**
         *
         */
        int ipCount = ipSet.size();
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(ipCount, //core pool size
            ipCount, //maximum pool size
            0L,       //keep alive time
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());

        final CountDownLatch countDownLatch = new CountDownLatch(ipCount);

        /**
         *
         */
        final int execCount = 50;
        List<Future<IpPingResult>> futureList = Lists.newArrayListWithExpectedSize(ipCount);
        for (String ip : ipSet) {

            Future<IpPingResult> f = threadPoolExecutor.submit(() -> {
                try {
                    IpPingResult ipPingResult = new SsLinkProxyChecker().ping(execCount, ip);

                    ipPingResult.setTitle(ipTitleMap.get(ip));

                    return ipPingResult;
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    countDownLatch.countDown();
                }
                return null;
            });

            futureList.add(f);

        }

        System.out.println("====== wait result =======");

        countDownLatch.await(1, TimeUnit.DAYS);

        List<IpPingResult> ipPingResults = Lists.newArrayListWithCapacity(ipCount);
        for (Future<IpPingResult> f : futureList) {

            IpPingResult r = f.get();
            System.out.println(r);
            ipPingResults.add(r);
        }

        System.out.println("=========== Final selected ============");
        if (ipPingResults.stream().filter(r->r.getLoss()<5).count()>2) {
        }
        ipPingResults.stream().filter(r -> r.getLoss() < 5).filter(r -> r.getAvgMills() < 300)
            .forEach(r -> System.out.println(r));
    }

    public IpPingResult ping(int count, String ipAddress) throws Exception {

        String pingCmd = String.format("ping -c %s %s", count, ipAddress);

        String log = String.format("---->Start to ping ip=%s with count=%s", ipAddress, count);
        System.out.println(log);

        Process pro = Runtime.getRuntime().exec(pingCmd);
        BufferedReader buf = new BufferedReader(new InputStreamReader(pro.getInputStream()));

        StringBuilder sb = new StringBuilder();

        String line = null;
        while ((line = buf.readLine()) != null) {
            if (!line.startsWith("64 bytes")) {
                sb.append(line);
            }
        }

        //        System.out.println(sb.toString());
        return new IpPingResult(sb.toString());
    }

    class IpPingResult {

        /** 标题 **/
        private String title;
        /** 丢失率 **/
        private float loss;
        private float minMills;
        private float avgMills;
        private float maxMills;
        private float stddevMills;

        public IpPingResult() {
        }

        public IpPingResult(String pingResult) {
            // loss packet
            String loss = StringUtils.substringBetween(pingResult, "packets received, ", "%");
            String statisticsStr = StringUtils.substringBetween(pingResult, "min/avg/max/stddev = ", " ms");

            this.loss = Float.parseFloat(loss);

            String[] metricMills = StringUtils.split(statisticsStr, "/");
            if (metricMills == null) {
                System.out.println(pingResult);
                return;

            }
            this.minMills = Float.parseFloat(metricMills[0]);
            this.avgMills = Float.parseFloat(metricMills[1]);
            this.maxMills = Float.parseFloat(metricMills[2]);
            this.stddevMills = Float.parseFloat(metricMills[3]);
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public float getLoss() {
            return loss;
        }

        public void setLoss(float loss) {
            this.loss = loss;
        }

        public float getMinMills() {
            return minMills;
        }

        public void setMinMills(float minMills) {
            this.minMills = minMills;
        }

        public float getAvgMills() {
            return avgMills;
        }

        public void setAvgMills(float avgMills) {
            this.avgMills = avgMills;
        }

        public float getMaxMills() {
            return maxMills;
        }

        public void setMaxMills(float maxMills) {
            this.maxMills = maxMills;
        }

        public float getStddevMills() {
            return stddevMills;
        }

        public void setStddevMills(float stddevMills) {
            this.stddevMills = stddevMills;
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }
    }

}
