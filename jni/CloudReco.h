/*==============================================================================
            Copyright (c) 2012 QUALCOMM Austria Research Center GmbH.
            All Rights Reserved.
            Qualcomm Confidential and Proprietary

@file
    CloudReco.h

@brief
    Sample for CloudReco

==============================================================================*/
#include <jni.h>
#include <android/log.h>
#include <stdio.h>
#include <string.h>
#include <assert.h>
#include <time.h>
#include <pthread.h>
#include <unistd.h>
#include <math.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <QCAR/QCAR.h>
#include <QCAR/UpdateCallback.h>
#include <QCAR/CameraDevice.h>
#include <QCAR/Renderer.h>
#include <QCAR/Area.h>
#include <QCAR/Rectangle.h>
#include <QCAR/VideoBackgroundConfig.h>
#include <QCAR/Trackable.h>
#include <QCAR/TrackableResult.h>
#include <QCAR/Tool.h>
#include <QCAR/Tracker.h>
#include <QCAR/TrackerManager.h>
#include <QCAR/ImageTracker.h>
#include <QCAR/ImageTargetResult.h>
#include <QCAR/CameraCalibration.h>
#include <QCAR/ImageTarget.h>
#include <QCAR/DataSet.h>
#include <QCAR/TargetFinder.h>
#include <QCAR/TargetSearchResult.h>
#include <QCAR/TrackableSource.h>
#include "SampleUtils.h"
#include "SampleMath.h"
#include "Texture.h"
#include "CubeShaders.h"
#include "Transition3Dto2D.h"

#ifdef __cplusplus
extern "C"
{
#endif

// ----------------------------------------------------------------------------
// 3d Plane Data for Displaying Book Overlay texture
// ----------------------------------------------------------------------------
static const float planeVertices[] =
{ -0.25, -0.25, 0.0, 0.25, -0.25, 0.0, 0.25, 0.25, 0.0, -0.25, 0.25, 0.0, };

static const float planeTexcoords[] =
{ 0.0, 0.0, 0.75f, 0.0, 0.75f, 0.75f, 0.0, 0.75f};

static const float planeNormals[] =
{ 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0 };

static const unsigned short planeIndices[] =
{ 0, 1, 2, 0, 2, 3 };

// ----------------------------------------------------------------------------
// 3d Augmented model Matrices
// ----------------------------------------------------------------------------
QCAR::Matrix44F modelViewMatrix;
QCAR::Matrix44F inverseProjMatrix;

// ----------------------------------------------------------------------------
// Application Render States
// ----------------------------------------------------------------------------
// Texture is Generated and Target is Acquired - Rendering Book Data
static int RS_NORMAL = 0;

// Target has been lost - Rendering transition to 2D Overlay
static int RS_TRANSITION_TO_2D = 1;

// Target has been reacquired - Rendering transition to 3D
static int RS_TRANSITION_TO_3D = 2;

// New Target has been found - Loading book data and generating OpenGL Textures
static int RS_LOADING = 3;

// Texture with book data has been generated in Java - Ready to be generated
// in OpenGL in the renderFrame thread
static int RS_TEXTURE_GENERATED = 4;

// CloudReco is active and scanning - Searching for targets.
static int RS_SCANNING = 5;

// Initialize RenderState
int renderState = RS_SCANNING;

// ----------------------------------------------------------------------------
// 3D to 2D Transition control variables
// ----------------------------------------------------------------------------
Transition3Dto2D* transition3Dto2D;
bool startTransition3Dto2D = false;
bool showAnimation3Dto2D = true;

// ----------------------------------------------------------------------------
// 2D to 3D Transition control variables
// ----------------------------------------------------------------------------
Transition3Dto2D* transition2Dto3D;
bool startTransition2Dto3D = false;
bool showAnimation2Dto3D = true;

float transitionDuration = 0.5f;
bool isShowing2DOverlay = false;

// ----------------------------------------------------------------------------
// Flag for deleting current product texture in the renderFrame Thread
// ----------------------------------------------------------------------------
bool deleteCurrentProductTexture = false;

// ----------------------------------------------------------------------------
// Trackable Data Global Variables
// ----------------------------------------------------------------------------
QCAR::Matrix34F pose;
QCAR::Vec2F targetSize;

// ----------------------------------------------------------------------------
// Info About the current tracked book
// Buffers for holding the unique ID of the last matched target and its 
// associated meta data
// ----------------------------------------------------------------------------
static const size_t CONTENT_MAX = 256;
char lastTargetId[CONTENT_MAX];
char targetMetadata[CONTENT_MAX];
pthread_mutex_t lastTargetIdMutex;

// ----------------------------------------------------------------------------
// Texture used to draw the plane with the book data
// ----------------------------------------------------------------------------
Texture *productTexture = 0;

// ----------------------------------------------------------------------------
// Flag to indicate if its currently tracking any object
// ----------------------------------------------------------------------------
bool trackingStarted = false;

// ----------------------------------------------------------------------------
// Methods Ids for calling Java Functions from Native Code
// ----------------------------------------------------------------------------
jmethodID createProductTextureID = 0;
jmethodID setStatusBarTextID = 0;
jmethodID hideStatusBarID = 0;
jmethodID showStatusBarID = 0;
jmethodID getProductTextureID = 0;
jmethodID showErrorMethodID = 0;
jmethodID enterContentModeID = 0;

// ----------------------------------------------------------------------------
// JNI Handles to the JavaVM and Activity instance:
// ----------------------------------------------------------------------------
JavaVM* javaVM = 0;
jobject activityObj = 0;

// ----------------------------------------------------------------------------
//Model drawing Variables
// ----------------------------------------------------------------------------
unsigned int shaderProgramID = 0;
GLint vertexHandle = 0;
GLint normalHandle = 0;
GLint textureCoordHandle = 0;
GLint mvpMatrixHandle = 0;

// ----------------------------------------------------------------------------
// Screen Dimensions
// ----------------------------------------------------------------------------
unsigned int screenWidth = 0;
unsigned int screenHeight = 0;

// ----------------------------------------------------------------------------
// Indicates whether screen is in portrait (true) or landscape (false) mode
// By default activity is in portrait
// ----------------------------------------------------------------------------
bool isActivityInPortraitMode = true;

// ----------------------------------------------------------------------------
// CloudReco Status
// ----------------------------------------------------------------------------
bool contentMode = false;
bool scanningMode = false;

// ----------------------------------------------------------------------------
// The projection matrix used for rendering virtual objects:
// ----------------------------------------------------------------------------
QCAR::Matrix44F projectionMatrix;

// ----------------------------------------------------------------------------
// Errors Management
// ----------------------------------------------------------------------------
double lastErrorMessageTime = 0;
int lastErrorCode = 0;

// ----------------------------------------------------------------------------
// Credentials for authenticating with the CloudReco service
// These are read-only access keys for accessing the image database
// specific to this sample application - the keys should be replaced
// by your own access keys. You should be very careful how you share
// your credentials, especially with untrusted third parties, and should
// take the appropriate steps to protect them within your application code
// ----------------------------------------------------------------------------
static const char* kAccessKey = "a309122e79a435b576dd4729103a6de5719f0a8a";
static const char* kSecretKey = "b6e6ee9fef8c5e98aee54576a351eb8f0efa8eef";

// ----------------------------------------------------------------------------
// Whether the cloudReco was started:
// ----------------------------------------------------------------------------
bool crStarted = false;

// ----------------------------------------------------------------------------
// This variable is used for giving the chance to get the target tracked again
// before starting the transition to 2D
// ----------------------------------------------------------------------------
int framesToSkipBeforeRenderingTransition = 10;
pthread_mutex_t framesToSkipMutex;

// ----------------------------------------------------------------------------
// ScaleFactor depending on different screen densities of devices
// ----------------------------------------------------------------------------
float scaleFactor = 1;
float dpiScaleIndicator = 1;
// ----------------------------------------------------------------------------
// Native Methods Called From Java
// ----------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudReco_setActivityPortraitMode(JNIEnv *, jobject, jboolean isPortrait);

JNIEXPORT int JNICALL
Java_ws_quill_notepeaker_CloudReco_initTracker(JNIEnv *, jobject);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudReco_deinitTracker(JNIEnv *, jobject);

JNIEXPORT int JNICALL
Java_ws_quill_notepeaker_CloudReco_initCloudReco(JNIEnv *, jobject);

JNIEXPORT int JNICALL
Java_ws_quill_notepeaker_CloudReco_deinitCloudReco(JNIEnv *, jobject);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudReco_productTextureIsCreated(JNIEnv *env,jobject);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudRecoRenderer_renderFrame(JNIEnv *, jobject);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudReco_initApplicationNative(
        JNIEnv* env, jobject obj, jint width, jint height);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudReco_deinitApplicationNative(
        JNIEnv* env, jobject obj);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudReco_startCamera(JNIEnv *, jobject);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudReco_stopCamera(JNIEnv *,jobject);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudReco_setProjectionMatrix(JNIEnv *, jobject);

JNIEXPORT jboolean JNICALL
Java_ws_quill_notepeaker_CloudReco_isCloudRecoStarted(JNIEnv*, jobject);

JNIEXPORT jboolean JNICALL
Java_ws_quill_notepeaker_CloudReco_activateFlash(JNIEnv*, jobject, jboolean flash);

JNIEXPORT jboolean JNICALL
Java_ws_quill_notepeaker_CloudReco_autofocus(JNIEnv*, jobject);

JNIEXPORT jboolean JNICALL
Java_ws_quill_notepeaker_CloudReco_setFocusMode(JNIEnv*, jobject, jint mode);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudRecoRenderer_initRendering(
        JNIEnv* env, jobject obj);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudRecoRenderer_updateRendering(
        JNIEnv* env, jobject obj, jint width, jint height);

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM* vm, void* reserved);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudReco_enterContentModeNative(JNIEnv*, jobject);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudReco_enterScanningModeNative(JNIEnv*, jobject);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudReco_setDeviceDPIScaleFactor(JNIEnv*, jobject, jfloat dpiSIndicator);

JNIEXPORT void JNICALL
Java_ws_quill_notepeaker_CloudReco_cleanTargetTrackedId(JNIEnv*, jobject);

// ----------------------------------------------------------------------------
// Class Methods
// ----------------------------------------------------------------------------
void showErrorMessage(int statusCode, double frameTime);
void createProductTexture(const char *targetMetadata);
void generateProductTextureInOpenGL();
void showStatusBar();
void hideStatusBar();
void setStatusBarText(const char* statusText);
void configureVideoBackground();
void renderAugmentation(const QCAR::TrackableResult* trackableResult);
void renderTransitionTo2D();
void renderTransitionTo3D();
void startTransitionTo2D();
void initStateVariables();
void enterContentMode();

