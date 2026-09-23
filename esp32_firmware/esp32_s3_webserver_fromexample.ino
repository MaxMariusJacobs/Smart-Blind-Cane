/**********************************************************************
  Filename    : Video Web Server (High FPS & Stability Edition)
  Description : Optimized Raw MJPEG stream.
**********************************************************************/
#include "esp_camera.h"
#include <WiFi.h>
#include <esp_http_server.h>
#include <esp_wifi.h>

// Select camera model
#define CAMERA_MODEL_ESP32S3_EYE // Has PSRAM
#include "camera_pins.h"

const char* ssid     = "Pixel 7a von Max";
const char* password = "Ralf1974";
camera_config_t config;

// --- Minimaler HTTP-Server für reinen Video-Stream ---
#define PART_BOUNDARY "123456789000000000000987654321"
static const char* _STREAM_CONTENT_TYPE = "multipart/x-mixed-replace;boundary=" PART_BOUNDARY;
static const char* _STREAM_BOUNDARY = "\r\n--" PART_BOUNDARY "\r\n";
static const char* _STREAM_PART = "Content-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n";

httpd_handle_t stream_httpd = NULL;

static esp_err_t stream_handler(httpd_req_t *req) {
  camera_fb_t * fb = NULL;
  esp_err_t res = ESP_OK;
  size_t _jpg_buf_len = 0;
  uint8_t * _jpg_buf = NULL;
  char part_buf[64];

  res = httpd_resp_set_type(req, _STREAM_CONTENT_TYPE);
  if (res != ESP_OK) return res;

  while (true) {
    fb = esp_camera_fb_get();
    if (!fb) {
      Serial.println("Camera capture failed");
      vTaskDelay(100 / portTICK_PERIOD_MS);
      continue;
    } else {
      _jpg_buf_len = fb->len;
      _jpg_buf = fb->buf;
    }
    
    // Native Chunk-Funktionen nutzen (Schützt vor abgebrochenen TCP-Sends)
    if (res == ESP_OK) {
      size_t hlen = snprintf(part_buf, 64, _STREAM_PART, _jpg_buf_len);
      res = httpd_resp_send_chunk(req, (const char *)part_buf, hlen);
    }
    if (res == ESP_OK) {
      res = httpd_resp_send_chunk(req, (const char *)_jpg_buf, _jpg_buf_len);
    }
    if (res == ESP_OK) {
      res = httpd_resp_send_chunk(req, _STREAM_BOUNDARY, strlen(_STREAM_BOUNDARY));
    }
    
    if (fb) {
      esp_camera_fb_return(fb);
      fb = NULL;
      _jpg_buf = NULL;
    }

    if (res != ESP_OK) break;

    // --- DER MAGISCHE FIX FÜR STABILITÄT ---
    // Pausiert die CPU für 15ms. Das gibt dem LwIP (WLAN-Prozess) zwingend 
    // die nötige Rechenzeit, um TCP-ACKs deines Handys zu verarbeiten. 
    // Verhindert Puffer-Stau und eliminiert das Stottern komplett.
    vTaskDelay(15 / portTICK_PERIOD_MS); 
  }
  return res;
}

void startCameraServer() {
  httpd_config_t server_config = HTTPD_DEFAULT_CONFIG(); 
  server_config.server_port = 80;
  
  // Stabilitäts-Optimierungen für den Stream
  server_config.max_open_sockets = 1;    // Nur ein Client, verhindert Speicher-Absturz
  server_config.lru_purge_enable = true; // Schließt tote Verbindungen sofort hart

  httpd_uri_t index_uri = {
    .uri       = "/",
    .method    = HTTP_GET,
    .handler   = stream_handler,
    .user_ctx  = NULL
  };

  if (httpd_start(&stream_httpd, &server_config) == ESP_OK) {
    httpd_register_uri_handler(stream_httpd, &index_uri);
  }
}
// --- Ende Minimaler HTTP-Server ---

void cameraInit(void);

void setup() {
  // Maximale CPU Leistung garantieren
  setCpuFrequencyMhz(240);
  
  Serial.begin(115200);
  Serial.setDebugOutput(true);
  Serial.println();

  cameraInit();
  
  WiFi.begin(ssid, password);

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.println("WiFi connected");

  // WLAN Energiesparmodus zwingend deaktiviert lassen!
  esp_wifi_set_ps(WIFI_PS_NONE);

  startCameraServer();

  Serial.print("Camera Ready! Use 'http://");
  Serial.print(WiFi.localIP());
  Serial.println("' to connect");
}

void loop() {
  delay(10000);
}

void cameraInit(void){
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  
  // Sensortakt auf 24 MHz hochschrauben für flüssigere Hardware-Erfassung
  config.xclk_freq_hz = 24000000; 
  
  config.frame_size = FRAMESIZE_QVGA;
  config.pixel_format = PIXFORMAT_JPEG;
  
  config.grab_mode = CAMERA_GRAB_LATEST; 
  config.fb_count = 2;                   
  
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.jpeg_quality = 20; 
  
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    if(err==ESP_ERR_NOT_SUPPORTED){
      config.pixel_format = PIXFORMAT_RGB565;
      esp_err_t err = esp_camera_init(&config);
      if (err != ESP_OK) {
        Serial.printf("Camera init failed with error 0x%x", err);
        return;
      }
    }
  }

  sensor_t * s = esp_camera_sensor_get();
  uint16_t pid = s->id.PID;
  if(pid == OV2640_PID){
    s->set_hmirror(s, 0);
    s->set_vflip(s, 0);     
  }
  else if(pid == OV3660_PID){
    s->set_hmirror(s, 1);
    s->set_vflip(s, 0);     
  }
  else if(pid == GC2145_PID){
    s->set_hmirror(s, 0);
    delay(500);
    s->set_vflip(s, 0);      
  }
  else if(pid == GC0308_PID){
    s->set_hmirror(s, 0);
    delay(500);
    s->set_vflip(s, 0);     
  }
  else{
    s->set_hmirror(s, 1);
    s->set_vflip(s, 0);       
  }
  s->set_brightness(s, 1);
  s->set_saturation(s, 0);
  s->set_ae_level(s, -3);
}