package net.oukranos.oreadv1.interfaces;

interface OreadServiceListener {
    void handleWaterQualityData();
    void handleOperationProcStateChanged();
    void handleOperationProcChanged();
    void handleOperationTaskChanged();
}

