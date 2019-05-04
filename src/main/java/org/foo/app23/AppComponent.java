/*
 * Copyright 2019-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.foo.app23;

import com.google.common.primitives.Longs;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.flow.*;
import org.onosproject.net.packet.*;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.onosproject.net.flow.FlowRuleEvent.Type.RULE_ADD_REQUESTED;


/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true, service = AppComponent.class)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    //----------------------- Servicios usados -------------------------------------------
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;


    //----------------- variables y objetos globales inicializados ----------------------
    private MonitoringPacketProcessor processor = new MonitoringPacketProcessor();
    private ApplicationId appId;
    private final short tipo = 21879; // En HEX: 0x5577
    private final short tipo2 = 26985; //En HEX: 0x6969
    private long t1 = 0; //tiempo inicial instanciar regla
    private long Tflowrule; //tiempo en instanciar regla
    private FlowId flowId;
    private Map<DeviceId,FlowId> FlowidRulesTYPE = new HashMap<DeviceId,FlowId>();
    private Collection flujos = new ArrayList<FlowRule>();
    private Timer timer;
    private long start; //se inicializa la variable que nos medirá el tiempo de inicio.
    private CrearCSV file;
    private int NumPacketsIn = 0;
    private final FlowRuleListener flowRuleListener = new InternalFlowRuleListener();
    private int countN = 0;
    private long lastT = 0;
    private long t = 0;


    //---------- tarea que se ejecuta cada intervalo (enviar un paquete UDP) -----------
    public class MyTimerTask extends TimerTask{
        public void run(){
            //--- se obtiene el ID de los switches y los puertos de uno en especifico---
            String Nsw = "001";
            String Nprt = "2";
            Map<String,DeviceId> swID = getIdSwitches();
            Map<String, PortNumber> portsNum = getPortsSwitch(swID.get(Nsw));
            //---------- Enviar paquete UDP --------------------------------------------
            SendPacketUDP(swID.get(Nsw),portsNum.get(Nprt));

            //-------- Intanciar regla de flujo para medir tiempo que demora -----------
            if (t1 == 0){
                installRuleTYPE2(swID.get(Nsw));//se pasa como parametro, el device id donde se instanciará
            }
        }
    }


    //--------- clase para el almacenamiento de informacion en un archivo CSV ---------
    public class CrearCSV {

        private FileWriter fw;
        private final String delim = ",";
        private final String NEXT_LINE = "\n";

        private void crearArchivoCSV(String file) {
            try {
                fw = new FileWriter(file);
                fw.append("#").append(delim);
                fw.append("TimeStart[ms]").append(delim);
                fw.append("TimeStop[ms]").append(delim);
                fw.append("TimeArrivedSwitch[ms]").append(delim);
                fw.append("TimeDeviceController[ms]").append(delim);
                fw.append("Latency[ms]").append(delim);
                fw.append("Time [s]").append(delim);
                fw.append("throughputBytes[Bytes/s]").append(delim);
                fw.append("throughputPackets[#/s]").append(delim);
                fw.append("NumePacketsIn").append(delim);
                fw.append("TimeInstanceFLowRule[ms]").append(NEXT_LINE);
                fw.flush();
            } catch (IOException e) {
               log.info(e.getMessage());
            }
        }

        private void EscribirCSV(String cont, String TimeStart, String TimeStop, String TimeArrivedSwitch,
                                 String TimeDeviceController, String Latency, String CurrentTime,
                                 String throughputBytes, String throughputPackets, String NumP, String TflowRule){
            try {
                fw.append(cont).append(delim);
                fw.append(TimeStart).append(delim);
                fw.append(TimeStop).append(delim);
                fw.append(TimeArrivedSwitch).append(delim);
                fw.append(TimeDeviceController).append(delim);
                fw.append(Latency).append(delim);
                fw.append(CurrentTime).append(delim);
                fw.append(throughputBytes).append(delim);
                fw.append(throughputPackets).append(delim);
                fw.append(NumP).append(delim);
                fw.append(TflowRule).append(NEXT_LINE);
                fw.flush();
            } catch (IOException e) {
                log.info(e.getMessage());
            }
        }

        private void CerrarArchivoCSV(){
            try {
                fw.close();
            }catch (IOException e){
                log.info(e.getMessage());
            }
        }
    }


        @Activate
    protected void activate() {
        log.info("/////////////////////////////////////////");
        log.info("Started .");
        flowRuleService.addListener(flowRuleListener);
        //se necesita un ID de la aplicacion para instanciar reglas de flujo.
        appId = coreService.registerApplication("org.foo.app");
        packetService.addProcessor(processor, PacketProcessor.director(1));
        installRuleTYPE(); //instala la regla permanente en cada switch para los paquetes con type = 0x5577

        //------------------ Activar las tareas ---------------------------------
        TimerTask timerTask = new MyTimerTask();
        //running timer task as daemon thread
        timer = new Timer(true);
        //tarea, retrazo antes de ejecutarse, el periodo de ejecucion.
        timer.schedule(timerTask,0,2000); //tarea 1, se ejecuta cada 2 segundos

        //-----------------Crear el archivo de excel CSV-------------------------
        file = new CrearCSV();
        String nombreDeArchivo = "/home/onos/archivo.csv";
        file.crearArchivoCSV(nombreDeArchivo);
        log.info("/////////////////////////////////////////");
    }


    @Deactivate
    protected void deactivate() {
        log.info("/////////////////////////////////////////");
        log.info("Stopped");
        flowRuleService.removeListener(flowRuleListener);
        packetService.removeProcessor(processor);
        timer.cancel();
        removeRuleType();//se elimina la regla permanente de cada switch
        file.CerrarArchivoCSV();
        log.info("/////////////////////////////////////////");
    }


    public double analyze(){
        //--- se obtiene el ID de los switches y los puertos de uno en especifico----
        String Nsw = "001";
        Map<String,DeviceId> swID = getIdSwitches();

        installRuleTYPE2(swID.get(Nsw));
        return 0;
    }


    private class MonitoringPacketProcessor implements PacketProcessor{
        @Override
        public void process(PacketContext context){

            NumPacketsIn += 1;

            InboundPacket pkt = context.inPacket();
            ConnectPoint received = pkt.receivedFrom();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt == null) {
                return;
            }
            if (isControlPacket(ethPkt)) {
                return;
            }
            if (ethPkt.getEtherType() == tipo){
                //-------------- Obtener el tiempo de envio desde el paquete capturado -----------------------
                byte[] payloadTime = context.outPacket().data().array();
                byte[] TimeStart = extractTimeStartPacket(payloadTime);
                ByteBuffer buffer = ByteBuffer.wrap(TimeStart);
                long START = buffer.getLong(); //Tiempo en que se envio el paquete capturado

                //-------------- Calculo de la Latencia ------------------------------------------------------
                long stop = System.currentTimeMillis();//TIempo en que se recibe el paquete en el controlador
                long timeArrivedToSwitch = context.time();//Tiempo en que llegó el paquete al switch que lo envio al controlador.
                long TDC = stop - timeArrivedToSwitch;
                long Latency = stop - START - 2*(TDC);

                //-------------- Calculo de throughput a un puerto de un switch -------------------------------
                PortStatistics portStatistics = deviceService.getDeltaStatisticsForPort(received.deviceId(),received.port());

                long bytesent = portStatistics.bytesSent();
                long bytereceived = portStatistics.bytesReceived();
                long packetsent = portStatistics.packetsSent();
                long packetreceived = portStatistics.packetsReceived();
                long duraction = portStatistics.durationSec();

                long ThroughputByPort = (bytesent + bytereceived)/duraction;
                long packetsbyPort = (packetsent + packetreceived)/duraction;

                //-------------- Guardar informacion en un archivo csv ---------------------------------------
                TimeZone.setDefault(TimeZone.getTimeZone("GMT-5"));
                DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
                Date date = new Date();
                //dateFormat.format(date)

                long currentT = System.currentTimeMillis();
                if (countN != 0){
                    t += (currentT - lastT)/1000;//tiempo en segundos
                }
                lastT = currentT;
                countN += 1;
                file.EscribirCSV(Integer.toString(countN),Long.toString(START), Long.toString(stop),
                        Long.toString(timeArrivedToSwitch), Long.toString(TDC), Long.toString(Latency),
                        Long.toString(t), Long.toString(ThroughputByPort), Long.toString(packetsbyPort),
                        Integer.toString(NumPacketsIn),Long.toString(Tflowrule));

                //-------------- Mostrar informacion en la consola de logs ------------------------------------
                log.info(Long.toString(Latency));
                log.info(pkt.receivedFrom().deviceId().toString());
                log.info(".....................................");
            }

            start = 0;
          }

          public byte[] extractTimeStartPacket(byte[] payload){
              int l = payload.length - 1;
              byte[] timerStart = new byte[8];
              int cont = 7;
              for (int i = l; i > (l-8); i--){
                  timerStart[cont] = payload[i];
                  cont--;
              }
              return timerStart;
          }
    }


    private class InternalFlowRuleListener implements FlowRuleListener {
        @Override
        public void event(FlowRuleEvent event) {
            // TODO handle flow rule event
            if (event.type() == RULE_ADD_REQUESTED){
                if (event.subject().id() == flowId){
                    long t2 = System.currentTimeMillis();
                    Tflowrule = t2 - t1;
                    t1 = 0; //reinicia el tiempo inicial para cumplir condicion de reenvio de nueva regla
                }
            }
        }
    }

    private void SendPacketUDP(DeviceId deviceId, PortNumber portNumber){
        //-------- Se crea un Payload con el tiempo de creacion del paquete ---------
        start = System.currentTimeMillis(); //se calcula el tiempo en que se envía el paquete.
        byte[] bytesPayload = Longs.toByteArray(start);
        Data dataPayload = new Data().setData(bytesPayload);
        //------------- se crea un segmento UDP -------------------------------------
        UDP udp = new UDP();
        udp.setPayload(dataPayload);
        //-------- se crea un paquete IPV4 que encapsula el paquete ICMP ------------
        IPv4 paquete = new IPv4();
        paquete.setDestinationAddress("10.0.0.3");
        paquete.setSourceAddress("10.0.0.2");
        paquete.setPayload(udp);
        //------ se crea una trama Ethernet que encapsula el paquete IPV4------------
        Ethernet trama = new Ethernet();
        trama.setSourceMACAddress(MacAddress.valueOf("00:00:00:00:00:02"));
        trama.setDestinationMACAddress(MacAddress.valueOf("00:00:00:00:00:04"));
        //trama.setEtherType(Ethernet.TYPE_IPV4);
        trama.setEtherType(tipo);
        trama.setPayload(paquete);
        //-----------se define el puerto de salida del paquete creado.---------------
        TrafficTreatment.Builder t = DefaultTrafficTreatment.builder();
        t.setOutput(portNumber);
        //-------- se crea y se envía el packet_out al switch especificado.----------
        OutboundPacket o = new DefaultOutboundPacket(deviceId,t.build(),ByteBuffer.wrap(trama.serialize()));
        packetService.emit(o);
    }


    // indica si es o no un paquete de control, e.g. LLDP, BDDP
    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }


    private void installRuleTYPE(){
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(tipo);
        //----------------------------------------------------------------------------
        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
        treatment.setOutput(PortNumber.CONTROLLER);
        //----------------------------------------------------------------------------
        DefaultFlowRule.Builder FlowRule = DefaultFlowRule.builder();
        FlowRule.makePermanent();
        FlowRule.withPriority(20);
        FlowRule.fromApp(appId);
        FlowRule.withSelector(selector.build());
        FlowRule.withTreatment(treatment.build());

        Map<String,DeviceId> swID = getIdSwitches();
        //recorrer un mapa como si fuera una arraylist.
        for (Map.Entry<String,DeviceId> entry : swID.entrySet()){
            FlowRule.forDevice(entry.getValue());//se define el id del switch donde se intanciará la regla
            FlowRule FlowRuleReady = FlowRule.build();//se contruye la regla
            //se almacena el id del switch y el id de la regla
            FlowidRulesTYPE.put(entry.getValue(),FlowRuleReady.id());
            //Almacena todos los flujos construidos que seran almacenados en cada switch
            flujos.add(FlowRuleReady);
            flowRuleService.applyFlowRules(FlowRuleReady);//se envía la regla al switch
        }
    }


    private void installRuleTYPE2(DeviceId deviceId){
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(tipo2);
        //----------------------------------------------------------------------------
        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
        treatment.drop();
        //----------------------------------------------------------------------------
        DefaultFlowRule.Builder FlowRule = DefaultFlowRule.builder();
        FlowRule.forDevice(deviceId);
        FlowRule.withHardTimeout(1);
        FlowRule.withPriority(40000);
        FlowRule.fromApp(appId);
        FlowRule.withSelector(selector.build());
        FlowRule.withTreatment(treatment.build());
        //---------------- Se instancia la regla de flujo de ida----------------------
        FlowRule regla = FlowRule.build();
        flowId = regla.id();
        t1 = System.currentTimeMillis();
        flowRuleService.applyFlowRules(regla);
    }


    private void removeRuleType(){
        Iterator <FlowRule> iterador = flujos.iterator();
        while (iterador.hasNext()){
            flowRuleService.removeFlowRules(iterador.next());
        }
    }


    public Map getIdSwitches(){
        Iterable<Device> devices = deviceService.getDevices();
        Map<String,DeviceId> swIDs = new HashMap<String, DeviceId>();
        for (Device d : devices){
            String id = d.id().toString();
            String key = id.substring(id.length()-3, id.length());
            swIDs.put(key,d.id());
        }
        return swIDs; //devulve un Mapa con clave, los ultimos 3 numero significativos del id (String) y como valor el id (DeviceId)
    }


    public Map getPortsSwitch(DeviceId deviceId){
        List<Port> ports = deviceService.getPorts(deviceId);
        Map<String, PortNumber> portsNum = new HashMap<String, PortNumber>();
        for (Port p : ports){
            portsNum.put(p.number().toString(), p.number());
        }
        return portsNum; //devulve un Mapa con clave el numero del puerto (String) y valor el puerto (PortNumber)
    }


}
