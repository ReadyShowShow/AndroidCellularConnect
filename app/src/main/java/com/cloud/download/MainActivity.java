package com.cloud.download;

import android.app.ProgressDialog;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private final static String DOWNLOAD_FILE_URL = "http://downapp.baidu.com/baidusearch/AndroidPhone/11.15.5.10/1/757p/20191027105115/baidusearch_AndroidPhone_11-15-5-10_757p.apk?responseContentDisposition=attachment%3Bfilename%3D%22baidusearch_AndroidPhone_757p.apk%22&responseContentType=application%2Fvnd.android.package-archive&request_id=1572931108_7074773481&type=static";
    //    private final static String DOWNLOAD_FILE_URL = "http://releases.ubuntu.com/19.10/ubuntu-19.10-desktop-amd64.iso";
    //    private final static String DOWNLOAD_FILE_URL = "https://www.singleton-associates.org/wp-content/uploads/2010/08/Free.jpg";
//    private final static String DOWNLOAD_FILE_URL = "https://v.meituan.net/mobile/app/Android/group-1000040203_2-meituan.apk/meituan";
//    private final static String DOWNLOAD_FILE_URL = "https://downapp.baidu.com/baidusearch/AndroidPhone/11.15.5.10/1/757b/20191027105115/baidusearch_AndroidPhone_11-15-5-10_757b.apk?responseContentDisposition=attachment%3Bfilename%3D%22baidusearch_AndroidPhone_757b.apk%22&responseContentType=application%2Fvnd.android.package-archive&request_id=1572934775_3833535911&type=static";
//    private final static String DOWNLOAD_FILE_URL = "http://dldir1.qq.com/qqmi/aphone_p2p/TencentVideo_V7.6.5.20239_848.apk";
    //    private final static String DOWNLOAD_FILE_URL = "https://speedtest1.he.chinamobile.com.prod.hosts.ooklaserver.net:8080/download?nocache=db3c521d-5091-4940-836a-09e81480026c&size=1048576";
    private TextView log;
    private LineChart chart;
    private final static int UPDATE_UI_SINGLE = 100;
    private OnDownloadChange onCellularDownloadChange = new OnDownloadChange();
    private OnDownloadChange onWifiDownloadChange = new OnDownloadChange();
    private Handler handler;

    private long refreshGap = 1000;

    ExecutorService threadPool = Executors.newCachedThreadPool();

    private boolean isAppFinishing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
    }

    private void initViews() {
        log = findViewById(R.id.log);
        chart = findViewById(R.id.chart1);

        chart.getDescription().setEnabled(false);
        chart.setDrawGridBackground(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(true);
        xAxis.setSpaceMin(20);
        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return String.valueOf(value * refreshGap / 1000f);
            }
        });

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setLabelCount(10, false);
        leftAxis.setAxisMinimum(0f); // this replaces setStartAtZero(true)

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                return formatWithUnit(value);
            }
        });
        rightAxis.setLabelCount(10, false);
        rightAxis.setDrawGridLines(false);
        rightAxis.setAxisMinimum(0f); // this replaces setStartAtZero(true)

        // set data
        chart.setData(new LineData());

        // do not forget to refresh the chart
        chart.setScaleYEnabled(false);
//        chart.animateX(750);

        handler = new Handler(getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case UPDATE_UI_SINGLE: {
                        updateUI();
                        handler.sendMessageDelayed(handler.obtainMessage(UPDATE_UI_SINGLE), refreshGap);
                        break;
                    }
                    default:
                }
            }
        };

        handler.sendMessageDelayed(handler.obtainMessage(UPDATE_UI_SINGLE), refreshGap);
    }

    private void updateUI() {
        float wifiSp = onWifiDownloadChange.getAndResetAccu() / (refreshGap / 1000);
        float cellularSp = onCellularDownloadChange.getAndResetAccu() / (refreshGap / 1000);
        updateChart(new float[]{wifiSp, cellularSp});
    }

    private void updateChart(float[] value) {
        LineData data = chart.getData();

        for (int index = 0; index < value.length; index++) {
            ILineDataSet set = data.getDataSetByIndex(index);
            if (set == null) {
                LineDataSet d = new LineDataSet(null, index == 0 ? "WiFi" : "Cellular");
                d.setLineWidth(2.5f);
                d.setCircleRadius(4.5f);
                d.setDrawValues(false);
                d.setHighLightColor(Color.rgb(244, 117, 117));
                if (index == 0) {
                    d.setColor(ColorTemplate.VORDIPLOM_COLORS[0]);
                    d.setCircleColor(ColorTemplate.VORDIPLOM_COLORS[0]);
                }
                set = d;
                data.addDataSet(set);
            }
            data.addEntry(new Entry(set.getEntryCount(), value[index]), index);
        }

        data.notifyDataChanged();
        // let the chart know it's data has changed
        chart.notifyDataSetChanged();
        // limit the number of visible entries
        chart.setVisibleXRangeMaximum(60);
        // chart.setVisibleYRange(30, AxisDependency.LEFT);

        // move to the latest entry
        chart.moveViewToX(data.getEntryCount());
    }

    public void startWifi(View v) {
        try {
            onWifiDownloadChange.setStop(false);
            final ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            // WiFi
            NetworkRequest wifiRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
            if (connectivityManager != null) {
                connectivityManager.requestNetwork(wifiRequest, new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        super.onAvailable(network);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                log.setText(log.getText() + " WiFi通道打开");
                            }
                        });
                        // 下载文件测试
                        downloadFile(network, onWifiDownloadChange, DOWNLOAD_FILE_URL);
                    }
                });
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void startCellular(View v) {
        try {
            onCellularDownloadChange.setStop(false);
            final ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            // 数据流量
            NetworkRequest cellularRequest = new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
            if (connectivityManager != null) {
                connectivityManager.requestNetwork(cellularRequest, new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        super.onAvailable(network);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                log.setText(log.getText() + " 数据流量通道打开");
                            }
                        });
                        // 下载文件测试
                        downloadFile(network, onCellularDownloadChange, DOWNLOAD_FILE_URL);
                    }
                });
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void stopDownload(View v) {
        onCellularDownloadChange.setStop(true);
        onWifiDownloadChange.setStop(true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        onCellularDownloadChange.setStop(true);
        onWifiDownloadChange.setStop(true);
        if (isAppFinishing) {
            return true;
        }
        isAppFinishing = true;

        // 停止图表刷新
        handler.removeMessages(UPDATE_UI_SINGLE);
        // 弹窗提示
        ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);
        progressDialog.setTitle("保存数据");
        progressDialog.setMessage("记录中");
        progressDialog.setCancelable(false);
        progressDialog.show();  //将进度条显示出来
        // 退出APP之前,将数据保存下来
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(3000);
                    Date d = new Date();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    File file = new File(getExternalFilesDir("log"), sdf.format(d) + ".md");
                    if (!file.getParentFile().exists()) {
                        file.getParentFile().mkdirs();
                    }
                    FileOutputStream outStream = new FileOutputStream(file, true);
                    // 写入表头
                    outStream.write(("次 | WiFi | cellular \n" +
                            "------- | ------- | ------- \n").getBytes());
                    try {
                        LineDataSet dataSet = (LineDataSet) chart.getLineData().getDataSets().get(0);
                        LineDataSet cellularDataSet = (LineDataSet) chart.getLineData().getDataSets().get(1);
                        int count = dataSet.getEntryCount();
                        for (int i = 0; i < count; i++) {
                            Entry entry = dataSet.getEntryForIndex(i);
                            long wifi = (long) entry.getY();
                            long cellular = (long) cellularDataSet.getEntryForIndex(i).getY();
                            outStream.write((String.format("%d | %d | %d \n", i, wifi, cellular)).getBytes());
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    outStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.this.finish();
                    }
                });
            }
        });
        return true;
    }

    /**
     * @param urlPath 下载路径
     * @return 返回下载文件
     */
    private void downloadFile(final Network network, final OnDownloadChange onDownloadChange, final String urlPath) {
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                downloadFileInner(network, onDownloadChange, urlPath);
            }
        });
    }

    private void downloadFileInner(Network network, OnDownloadChange onDownloadChange, String urlPath) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.socketFactory(network.getSocketFactory());
        OkHttpClient client = builder.build();
        Request request = new Request.Builder().url(urlPath).build();
        InputStream is = null;
        try {
            Response response = client.newCall(request).execute();
            is = response.body().byteStream();
            long total = response.body().contentLength();
            int len;
            byte[] buf = new byte[2048];
            while (!onDownloadChange.isStop() && (len = is.read(buf)) != -1) {
                onDownloadChange.accumulate(len);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class OnDownloadChange {
        private long accu = 0;

        private boolean stop;

        public boolean isStop() {
            return stop;
        }

        public void setStop(boolean stop) {
            this.stop = stop;
        }

        public void accumulate(int size) {
            synchronized (this) {
                accu += size;
            }
        }

        public long getAndResetAccu() {
            synchronized (this) {
                long l = this.accu;
                accu = 0;
                return l;
            }
        }
    }

    /**
     * bytes转m或者G
     * 若≥1G，则显示x.xG；若＜1G，则显示xxm
     */
    public static String formatWithUnit(float bytes) {
        final int unit = 1000;
        final float kb = unit;
        if (bytes < kb) {
            return String.format("%.1fB", bytes);
        }
        final float mb = unit * unit;
        if (bytes < mb) {
            return String.format("%.1fK", bytes / kb);
        }
        final float gb = unit * unit * unit;
        if (bytes < gb) {
            return String.format("%.1fM", bytes / mb);
        }
        return String.format("%.1fG", gb);
    }
}

