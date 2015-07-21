package net.oukranos.oreadv1.interfaces;
import net.oukranos.oreadv1.types.OreadServiceWaterQualityData;
import net.oukranos.oreadv1.types.OreadServiceControllerStatus;
import net.oukranos.oreadv1.interfaces.OreadServiceListener;

interface OreadServiceApi {
    void start();
    void stop();
    String runCommand(String command, String params);
    OreadServiceWaterQualityData getData();
    OreadServiceControllerStatus getStatus();
    String getLogs(int lines);
    void addListener(OreadServiceListener listener);
    void removeListener(OreadServiceListener listener);
}