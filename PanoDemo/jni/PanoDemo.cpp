/*
 * An Android Panorama demo for DJI Inspire1 and Phantom 3 Professional using DJI SDK and OpenCV
 * Develop environment:jdk 8u45 + eclipse mars + ADT 23.0.6 + ndk r10e + cdt8.7.0 + cygwin2.1.0 + OpenCV2.4.11 + DJI SDK 2.3.0
 */
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <iostream>
#include <fstream>
#include <opencv2/highgui/highgui.hpp>
#include <opencv2/stitching/stitcher.hpp>
#include <vector>
#include <android/log.h>

using namespace std;
using namespace cv;

#define  LOG_TAG    "PanoDemojni"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define  cutBlackThreshold   0.001  //Threshold for cut black
//for jni call java method,Tutorial:http://www.mobileway.net/2015/03/20/android-pro-tip-call-java-methods-from-c-using-jni/
jclass javaClassRef;
jmethodID javaMethodRef;

extern "C"
{
//testjni
JNIEXPORT jstring JNICALL Java_com_dji_dev_panodemo_MainActivity_testjni(JNIEnv *env, jobject obj);
//OpenCV Stitching(return:0-success,other-error)
JNIEXPORT jint JNICALL Java_com_dji_dev_panodemo_MainActivity_jnistitching(JNIEnv *env, jobject obj, jobjectArray source, jstring result, jdouble scale);
}

//check row
bool checkRow(const cv::Mat& roi, int y)
{
	int zeroCount = 0;
    for(int x=0; x<roi.cols; x++)
    {
        if(roi.at<uchar>(y, x) == 0)
        {
        	zeroCount++;
        }
    }
    if((zeroCount/(float)roi.cols)>cutBlackThreshold)
    {
    	return false;
    }
    return true;
}

//check col
bool checkColumn(const cv::Mat& roi, int x)
{
	int zeroCount = 0;
    for(int y=0; y<roi.rows; y++)
    {
        if(roi.at<uchar>(y, x) == 0)
        {
        	zeroCount++;
        }
    }
    if((zeroCount/(float)roi.rows)>cutBlackThreshold)
    {
    	return false;
    }
    return true;
}

//find largest roi
bool cropLargestPossibleROI(const cv::Mat& gray, cv::Mat& pano, cv::Rect startROI)
{
    // evaluate start-ROI
    Mat possibleROI = gray(startROI);
    bool topOk = checkRow(possibleROI, 0);
    bool leftOk = checkColumn(possibleROI, 0);
    bool bottomOk = checkRow(possibleROI, possibleROI.rows-1);
    bool rightOk = checkColumn(possibleROI, possibleROI.cols-1);
    if(topOk && leftOk && bottomOk && rightOk)
    {
        // Found!!
    	LOGE("cropLargestPossibleROI success");
        pano = pano(startROI);
        return true;
    }
    // If not, scale ROI down
    Rect newROI(startROI.x, startROI.y, startROI.width, startROI.height);
    // if x is increased, width has to be decreased to compensate
    if(!leftOk)
    {
    	newROI.x++;
    	newROI.width--;
    }
    // same is valid for y
    if(!topOk)
    {
    	newROI.y++;
    	newROI.height--;
    }
    if(!rightOk)
    {
    	newROI.width--;
    }
    if(!bottomOk)
    {
    	newROI.height--;
    }
    if(newROI.x + startROI.width < 0 || newROI.y + newROI.height < 0)
    {
    	//sorry...
    	LOGE("cropLargestPossibleROI failed");
        return false;
    }
    return cropLargestPossibleROI(gray,pano,newROI);
}

//testjni
JNIEXPORT jstring JNICALL Java_com_dji_dev_panodemo_MainActivity_testjni(JNIEnv *env, jobject obj)
{
	return env->NewStringUTF("Hello from native code!");
}

//OpenCV Stitching(return:0-success,other-error)
JNIEXPORT jint JNICALL Java_com_dji_dev_panodemo_MainActivity_jnistitching(JNIEnv *env, jobject obj, jobjectArray source, jstring result, jdouble scale)
{
	clock_t beginTime, endTime;
	double timeSpent;
	beginTime = clock();
	//init jni call java method
	static int once = 1;
	if(once)
	{
	    jclass thisClass = env->GetObjectClass(obj);
	    javaClassRef = (jclass) env->NewGlobalRef(thisClass);
	    javaMethodRef = env->GetMethodID(javaClassRef, "javaShowJniStitchingCostTime", "(D)V");
	    once = 0;
	}

	int i = 0;
	bool try_use_gpu = false;
	vector<Mat> imgs;
	Mat img;
	Mat img_scaled;
	Mat pano;
	Mat pano_tocut;
	Mat gray;

	const char* result_name = env->GetStringUTFChars(result, JNI_FALSE);  //convert result
	LOGE("result_name=%s",result_name);
	LOGE("scale=%f",scale);
	//jsize result_length = env->GetStringLength(result);
	//LOGE("result_length=%d\n",result_length);

	int imgCount = env->GetArrayLength(source); //img count
	LOGE("source imgCount=%d",imgCount);
	for(i=0;i<imgCount;i++)
	{
		jstring jsource = (jstring)(env->GetObjectArrayElement(source, i));
		const char* source_name = env->GetStringUTFChars(jsource, JNI_FALSE);  //convert jsource
		LOGE("Add index %d source_name=:%s", i, source_name);
		img=imread(source_name);
		Size dsize = Size((int)(img.cols*scale),(int)(img.rows*scale));
		img_scaled = Mat(dsize,CV_32S);
		resize(img,img_scaled,dsize);
		imgs.push_back(img_scaled);
		env->ReleaseStringUTFChars(jsource, source_name);  //release convert jsource
	}
	img.release();

	Stitcher stitcher = Stitcher::createDefault(try_use_gpu);
	Stitcher::Status status = stitcher.stitch(imgs, pano);
	if (status != Stitcher::OK)
	{
		LOGE("stitching error");
		return (int)status;
	}
	//release imgs
	for(i=0;i<imgs.size();i++)
	{
		imgs[i].release();
	}

	//cut black edges
	//LOGE("stitching success,cutting black....");
	pano_tocut = pano;
	cvtColor(pano_tocut, gray, CV_BGR2GRAY);
    Rect startROI(0,0,gray.cols,gray.rows); // start as the source image - ROI is the complete SRC-Image
    cropLargestPossibleROI(gray,pano_tocut,startROI);
	gray.release();

	imwrite(result_name, pano_tocut);
	pano.release();
	pano_tocut.release();
	env->ReleaseStringUTFChars(result, result_name);  //release convert result
	endTime = clock();
	timeSpent = (double)(endTime - beginTime) / CLOCKS_PER_SEC;
	LOGE("success,total cost time %f seconds",timeSpent);
	env->CallVoidMethod(obj, javaMethodRef, timeSpent);
	return 0;
}
