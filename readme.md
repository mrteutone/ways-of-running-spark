# 1001 ways to run Spark jobs
So you have a fancy Spark job written in Scala and managed by `sbt`.
Now you wonder how to actually run it.
Just get inspired by the list below (work-in-progress).

Besides the readme this repository contains a Spark job.
All instructions below are based on it.
See the next section for more details about the code.  

## About the Spark job
The job reads publicly available trading data.
It aggregates the trading activity from minutes to daily
and adds the difference to last day's closing price. 
The output is stored in CSV format.

Within the standard sbt project structure you will find:
* Actual Spark job: `src/main/scala/StockAggregator.scala`.
* Unit tests: `src/test/scala/StockAggregatorTest.scala`.

## Reoccurring instructions
Many ways of running Spark have some steps in common like downloading Spark.
Reoccurring steps are factored out and are listed here in order to reduce redundancy.
You only need to execute them if they are mentioned in the actual list below.

### Downloading Spark
```shell script
SPARK_VERSION="3.0.0-preview2"
SPARK_FILE="spark-${SPARK_VERSION}-bin-hadoop3.2"
wget https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/${SPARK_FILE}.tgz
tar -xvzf ${SPARK_FILE}.tgz --one-top-level=spark --strip-components=1
sparkReleasePath="$(pwd)/spark"
```

### Get the source code
<a name="get-source-code"></a>
Download the content of this repo by:
```shell script
cd ~  # Change to your preferred directory
wget https://github.com/mrteutone/ways-of-running-spark/archive/master.tar.gz
tar -xvzf master.tar.gz
sbtProject="$(pwd)/ways-of-running-spark"
```

### Build a fat jar
#### Alternative 1: install `sbt` locally
1. Install `sbt`
2. Download the source code:

   [#manual](#get-source-code)

   [#manual](get-source-code)

   [#manual](#user-content-get-the-source-code)

   [#manual](#get-the-source-code)


```shell script
cd "$sbtProject"
sbt clean assembly
sbtTargetDir="$sbtProject/target/scala-2.12"
jarName="ways-of-running-spark-assembly-0.1.jar"
jarFullPath="$sbtTargetDir/$jarName"
log4jConfRelativePath="classes/log4j.properties"
log4jConfFullPath="$sbtTargetDir/$log4jConfRelativePath"
```

### Build Spark Docker image
1. Download Spark
2. Build the docker image:
   ```shell script
   cd $sparkReleasePath
   sed -i.backup "s/FROM openjdk:[0-9]\+-jdk-slim/FROM openjdk:11-jdk-slim/" kubernetes/dockerfiles/spark/Dockerfile
   ./bin/docker-image-tool.sh -f kubernetes/dockerfiles/spark/Dockerfile build
   ```

## 1. Develop locally, run locally as process within docker container
```shell script
dockerImage="eed3si9n/sbt:jdk11-alpine"
docker pull $dockerImage

docker run -it \
  --mount src=$sbtProject,target=/opt/workspace,type=bind \
  --entrypoint sbt \
  $dockerImage "
    run
      --ISIN DE0005772206
      --output deutsche-boerse-xetra-pds/processed/
      --replace
      --spark
        spark.master=local[*]
        spark.hadoop.fs.s3a.impl=org.apache.hadoop.fs.s3a.S3AFileSystem
        spark.hadoop.fs.s3a.aws.credentials.provider=org.apache.hadoop.fs.s3a.AnonymousAWSCredentialsProvider
        spark.ui.showConsoleProgress=true
  "
```

Besides unit tests this is the fastest way of developing and/or testing your Spark job.
This way of running Spark is known as [self-contained application][2].
If you change something in the code just restart the docker container and issue your `sbt run` again.
All dependencies are managed by `sbt` and you don't need to download Spark manually. 
It's also a good way to share your code with others.
They only need docker, your code and the commands above. 
The docker image (see [docker hub][1-docker-hub] and [github][1-github]) based on [Alpine Linux][3] contains openJDK 11 and sbt.
It is maintained by the former sbt lead Eugene Yokota so you don't even need your own Dockerfile.

[1-docker-hub]: https://hub.docker.com/r/eed3si9n/sbt
[2]: https://spark.apache.org/docs/latest/quick-start.html#self-contained-applications
[3]: https://en.wikipedia.org/wiki/Alpine_Linux
[1-github]: https://github.com/eed3si9n/docker-sbt

## 2. Download Spark release, submit job locally
1. Build a fat jar for your project: [fat-jar](#user-content-build-a-fat-jar)
2. Download a Spark release [spark-download](#user-content-downloading-spark) and go into the installed path: `cd $sparkReleasePath`
3. Execute:
    ```shell script
    $sparkReleasePath/bin/spark-submit \
      --verbose \
      --master local[*] \
      --name spark-test \
      --class StockAggregator \
      --conf spark.driver.extraJavaOptions="-Dlog4j.configuration=file://$log4jConfFullPaths" \
      --conf spark.executor.extraJavaOptions="-Dlog4j.configuration=file://$log4jConfFullPath" \
      --conf spark.hadoop.fs.s3a.impl="org.apache.hadoop.fs.s3a.S3AFileSystem" \
      --conf spark.hadoop.fs.s3a.aws.credentials.provider="org.apache.hadoop.fs.s3a.AnonymousAWSCredentialsProvider" \
      --conf spark.ui.showConsoleProgress=true \
      $jarFullPath --ISIN DE0005772206 --replace
    ```

## 3. Submit job locally, official Spark Dockerfile
You don't like downloading random things but decided to give it a try:

1. Download Spark
2. Build Spark docker image
3. Build a fat jar

Congratulations, you now have your official Spark docker image ready to go.
Now just submit your cool stuff to a docker container:

```shell script
docker run -it --rm \
  --mount src=$sbtProject/target/scala-2.12,target=/opt/workspace,type=bind \
  spark:latest /opt/spark/bin/spark-submit \
    --verbose \
    --master local[*] \
    --name spark-test \
    --class StockAggregator \
    --conf spark.driver.extraJavaOptions="-Dlog4j.configuration=file:///opt/workspace/$log4jConfRelativePath" \
    --conf spark.executor.extraJavaOptions="-Dlog4j.configuration=file:///opt/workspace/$log4jConfRelativePath" \
    --conf spark.hadoop.fs.s3a.impl="org.apache.hadoop.fs.s3a.S3AFileSystem" \
    --conf spark.hadoop.fs.s3a.aws.credentials.provider="org.apache.hadoop.fs.s3a.AnonymousAWSCredentialsProvider" \
    --conf spark.ui.showConsoleProgress=true \
    /opt/workspace/$jarName \
      --ISIN DE0005772206 \
      --replace
```

## 4. Locally in Kubernetes via Minikube
Nowadays everybody wants to run everything in Kubernetes.
Here you go:

1. Install `kubectl` via https://kubernetes.io/docs/tasks/tools/install-kubectl
   Test successful install with `kubectl version --client`.
   On *Debian* consider
   [install using native package management](https://kubernetes.io/docs/tasks/tools/install-kubectl/#install-using-native-package-management). 

2. Download Minikube:
   ```shell script
   curl -Lo minikube https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
   chmod +x minikube
   mv minikube ~/bin/  # Works b/c $PATH contains $HOME/bin
   ```

3. Start Kubernetes cluster:
   ```shell script
   minikube start --driver=docker --cpus=4 --memory=8g --kubernetes-version=1.15.3
   minikube status
   kubectl api-versions | grep rbac  # Check that rbac is enabled
   ```

   Note that the version displayed by `ls jars/kubernetes-client*`
   must be mapped to a compatible Kubernetes version by using
   [kubernetes compatibility matrix](https://github.com/fabric8io/kubernetes-client/blob/master/README.md#compatibility-matrix).
   In this case `4.6.4` -> `1.15.3`

4. Set access rights for Spark:
   ```shell script
   kubectl create serviceaccount spark
   
   kubectl create clusterrolebinding spark-role \
     --clusterrole=cluster-admin \
     --serviceaccount=default:spark \
     --namespace=default
   ```
   
   Check if everything works:
   ```shell script
   kubectl get sa
   kubectl get clusterrolebinding spark-role -o yaml
   ```

5. As the Spark job generates a CSV file we need to attach a volume to the cluster.
   Just following instructions are taken from the
   [official Kubernetes docs](https://kubernetes.io/docs/tasks/configure-pod-container/configure-persistent-volume-storage/):
   
   1. *ssh* into the Kubernetes node and create a directory where the output will be stored in:
      ```shell script
      minikube ssh
      sudo mkdir -p /mnt/data
      sudo chmod go+w /mnt/data
      ``` 
   
   2. Create `pv-volume.yaml` with content:
      ```yaml
      apiVersion: v1
      kind: PersistentVolume
      metadata:
        name: my-pv-volume
        labels:
          type: local
      spec:
        storageClassName: manual
        capacity:
          storage: 10Gi
        accessModes:
          - ReadWriteOnce
        hostPath:
          path: "/mnt/data"
      ```

   3. Create `pv-claim.yaml` with content:  
      ```yaml
      apiVersion: v1
      kind: PersistentVolumeClaim
      metadata:
        name: my-pv-claim
      spec:
        storageClassName: manual
        accessModes:
          - ReadWriteOnce
      resources:
         requests:
           storage: 3Gi
      ```
   
   4. Execute:
   
          kubectl apply -f pv-volume.yaml
          kubectl get pv
          kubectl apply -f pv-claim.yaml
          kubectl get pvc

6. Download Spark

7. Create a docker image accessible by Minikube:
   1. Follow the instructions printed by `minikube docker-env`
   2. Build a fat jar
   3. Copy the fat jar and log4j conf so that it will be picked up when building the Spark docker image:
   
          cp "$log4jConfFullPath" "$jarFullPath" "$sparkReleasePath/examples/"

   4. Build Spark docker image

8. Submit Spark job to Kubernetes:
     ```shell script
     log4jConfPath="file:///opt/spark/examples/log4j.properties"
   
     $sparkReleasePath/bin/spark-submit \
       --verbose \
       --master k8s://https://172.17.0.2:8443 \
       --deploy-mode cluster \
       --name spark-test \
       --class StockAggregator \
       --conf spark.kubernetes.namespace=default \
       --conf spark.kubernetes.container.image=spark:latest \
       --conf spark.kubernetes.container.imagePullPolicy=Never \
       --conf spark.kubernetes.authenticate.driver.serviceAccountName=spark \
       --conf spark.kubernetes.context=minikube \
       --conf spark.driver.memory=2g \
       --conf spark.driver.extraJavaOptions="-Dlog4j.debug=true -Dlog4j.configuration=$log4jConfPath" \
       --conf spark.executor.instances=2 \
       --conf spark.executor.memory=3g \
       --conf spark.executor.extraJavaOptions="-Dlog4j.configuration=$log4jConfPath" \
       --conf spark.hadoop.fs.s3a.impl="org.apache.hadoop.fs.s3a.S3AFileSystem" \
       --conf spark.hadoop.fs.s3a.aws.credentials.provider="org.apache.hadoop.fs.s3a.AnonymousAWSCredentialsProvider" \
       --conf spark.kubernetes.driver.volumes.persistentVolumeClaim.my-pv-volume.options.claimName=my-pv-claim \
       --conf spark.kubernetes.driver.volumes.persistentVolumeClaim.my-pv-volume.mount.path=/mnt/data \
       --conf spark.kubernetes.driver.volumes.persistentVolumeClaim.my-pv-volume.mount.readOnly=false \
       --conf spark.kubernetes.executor.volumes.persistentVolumeClaim.my-pv-volume.options.claimName=my-pv-claim \
       --conf spark.kubernetes.executor.volumes.persistentVolumeClaim.my-pv-volume.mount.path=/mnt/data \
       --conf spark.kubernetes.executor.volumes.persistentVolumeClaim.my-pv-volume.mount.readOnly=false \
       --conf spark.ui.showConsoleProgress=true \
       local:///opt/spark/examples/$jarName \
         --ISIN DE0005772206 \
         --output /mnt/data/deutsche-boerse-xetra-pds/processed/
         --replace
     ```
   
   Notes:
   * Check the context with `kubectl config current-context`
   * Get the cluster's ip address with `kubectl cluster-info`

9. Check logs/result

   Find out the Spark driver's pod name with
   
       kubectl get pods
   
   It will be s.th. like `spark-test-...-driver`.
   Then you can check the logs with:

       kubectl logs -f spark-test-...-driver
   
   Wait until you see the following line in the logs (if no error occurs):
   
       CSV file successfully written to /mnt/data/deutsche-boerse-xetra-pds/processed/
   
   Then copy the output to a local directory:
   
       scp -ri $(minikube ssh-key) docker@$(minikube ip):/mnt/data ~/Downloads/minikube

10. Spark worker pods will be automatically deleted if you delete the Spark driver pod, i. e.

        kubectl delete pod spark-test-...-driver

## 5. EC2 with CloudFormation
to do

## 6. ECS
to do

## 7. EKS
to do

## 8. EMR
to do

## 9. spark-on-k8s-operator
[to do](https://github.com/GoogleCloudPlatform/spark-on-k8s-operator)
