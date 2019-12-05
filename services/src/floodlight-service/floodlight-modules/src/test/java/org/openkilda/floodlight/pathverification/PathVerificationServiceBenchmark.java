/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.floodlight.pathverification;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import org.openkilda.floodlight.service.FeatureDetectorService;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.algorithms.Algorithm;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.EasyMock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.projectfloodlight.openflow.protocol.OFDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class PathVerificationServiceBenchmark {
    @State(Scope.Benchmark)
    public static class SharedPathVerificationService {

        private Random random = new Random();
        private PathVerificationService pvs;
        private Algorithm algorithm;

        @Setup
        public void setUp() throws Exception {
            PathVerificationServiceConfig config = EasyMock.createMock(PathVerificationServiceConfig.class);
            expect(config.getVerificationBcastPacketDst()).andReturn("00:26:E1:FF:FF:FF").anyTimes();
            replay(config);

            pvs = new PathVerificationService();
            pvs.setConfig(config);

            FloodlightModuleContext fmc = new FloodlightModuleContext();
            FeatureDetectorService featureDetectorService = new FeatureDetectorService();
            featureDetectorService.setup(new FloodlightModuleContext());
            fmc.addService(FeatureDetectorService.class, featureDetectorService);
            fmc.addConfigParam(pvs, "hmac256-secret", "secret");
            pvs.init(fmc);

            algorithm = Algorithm.HMAC256("secret");
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    public void benchmarkGenerateDiscoveryPacket(SharedPathVerificationService shared, Blackhole blackhole) {
        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(DatapathId.of(shared.random.nextLong())).anyTimes();
        expect(sw.getLatency()).andReturn(U64.of(Math.abs(shared.random.nextInt() % 1000))).anyTimes();
        expect(sw.getInetAddress()).andReturn(new InetSocketAddress("192.168.10.101",
                Math.abs(shared.random.nextInt() % 1000))).anyTimes();
        expect(sw.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw.isActive()).andReturn(true).anyTimes();
        expect(sw.getNumTables()).andReturn((short) 8).anyTimes();

        OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
        expect(sw.getOFFactory()).andReturn(factory).anyTimes();
        OFDescStatsReply swDesc = factory.buildDescStatsReply().build();
        expect(sw.getSwitchDescription()).andReturn(new SwitchDescription(swDesc)).anyTimes();

        replay(sw);

        OFPacketOut packet = shared.pvs.generateDiscoveryPacket(sw,
                OFPort.of(Math.abs(shared.random.nextInt() % 1000)), true, shared.random.nextLong());
        blackhole.consume(packet.getData());
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    public void benchmarkSignWithSwitchMock(SharedPathVerificationService shared, Blackhole blackhole) {
        IOFSwitch sw = EasyMock.createMock(IOFSwitch.class);
        expect(sw.getId()).andReturn(DatapathId.of(shared.random.nextLong())).anyTimes();
        expect(sw.getLatency()).andReturn(U64.of(Math.abs(shared.random.nextInt() % 1000))).anyTimes();
        expect(sw.getInetAddress()).andReturn(new InetSocketAddress("192.168.10.101",
                Math.abs(shared.random.nextInt() % 1000))).anyTimes();
        expect(sw.hasAttribute(IOFSwitch.PROP_SUPPORTS_OFPP_TABLE)).andReturn(true).anyTimes();
        expect(sw.isActive()).andReturn(true).anyTimes();
        expect(sw.getNumTables()).andReturn((short) 8).anyTimes();

        OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);
        expect(sw.getOFFactory()).andReturn(factory).anyTimes();
        OFDescStatsReply swDesc = factory.buildDescStatsReply().build();
        expect(sw.getSwitchDescription()).andReturn(new SwitchDescription(swDesc)).anyTimes();

        replay(sw);

        Builder builder = JWT.create()
                .withClaim("dpid", sw.getId().getLong())
                .withClaim("ts", System.currentTimeMillis() + sw.getLatency().getValue())
            .withClaim("id", shared.random.nextLong());
        String token = builder.sign(shared.algorithm);
        blackhole.consume(token);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    public void benchmarkJwtSignOnly(SharedPathVerificationService shared, Blackhole blackhole) {
        Builder builder = JWT.create()
                .withClaim("dpid", shared.random.nextLong())
                .withClaim("ts", System.currentTimeMillis() + Math.abs(shared.random.nextInt() % 1000))
                .withClaim("id", shared.random.nextLong());
        String token = builder.sign(shared.algorithm);
        blackhole.consume(token);
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Fork(1)
    @Warmup(iterations = 1)
    @Measurement(iterations = 10)
    public void benchmarkHmac256SignOnly(SharedPathVerificationService shared, Blackhole blackhole) {
        ByteBuffer buffer = ByteBuffer.allocate(8 * Long.BYTES);
        "KILDA".chars().forEach(buffer::putInt); //title
        buffer.putLong(0x12345678); //header 1
        buffer.putLong(0xFFFFFFFF); //header 2
        buffer.putLong(shared.random.nextLong()); //dpid
        buffer.putLong(System.currentTimeMillis() + Math.abs(shared.random.nextInt() % 1000)); //ts
        buffer.putLong(shared.random.nextLong()); //id
        byte[] token = shared.algorithm.sign(buffer.array());
        blackhole.consume(token);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(PathVerificationServiceBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
