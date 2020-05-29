# 1001 ways to run Spark jobs
What are the possibilities to run your fancy Spark?
Just get inspired by the list below (work-in-progress).
Atm only Spark jobs written in Scala and managed by `sbt` is covered.
Python will be added in the future.
This repository contains a Spark job which is used throughout all examples in this readme.
Just use your own `sbt` project or use the code here as template to get started.
See the next section for more details about the code and how to adapt it.

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

<a name="get-sources"></a>
### Get the source code
Download the content of this repo by:
```shell script
cd ~  # Change to your preferred directory
wget https://github.com/mrteutone/ways-of-running-spark/archive/master.tar.gz
tar -xvzf master.tar.gz --one-top-level=ways-of-running-spark --strip-components=1
sbtProject="$(pwd)/ways-of-running-spark"
```

<a name="build-jar"></a>
### Build a fat jar
1. Define environment variable for later use:
  
       targetSubPath="target/scala-2.12"

2. **Either** build the jar locally:
   1. Install `sbt`
   2. Download the source code: [#manual](#get-sources)
   3. Execute:
      ```shell script
      cd "$sbtProject"
      sbt clean assembly
      ```  
   **or** build within a Docker image:
   ```shell script
   docker build -t sbt:test https://github.com/mrteutone/ways-of-running-spark.git
   containerId=$(docker create sbt:test)
   sbtProject="$HOME/ways-of-running-spark"  # $HOME is only a suggestion
   docker cp $containerId:/opt/workspace/$targetSubPath/ $sbtProject/
   docker rm -v $containerId
   ```

3. Define environment variables for later usage:
   ```shell script
   sbtTargetDir="$sbtProject/$targetSubPath"
   jarName="ways-of-running-spark-assembly-0.1.jar"
   jarFullPath="$sbtTargetDir/$jarName"
   log4jConfRelativePath="classes/log4j.properties"
   log4jConfFullPath="$sbtTargetDir/$log4jConfRelativePath"
   ```

<a name="download-spark"></a>
### Download Spark
Change to a directory where you want to download the files into.
Then execute:

```shell script
SPARK_VERSION="3.0.0-preview2"
SPARK_FILE="spark-${SPARK_VERSION}-bin-hadoop3.2"
wget https://archive.apache.org/dist/spark/spark-${SPARK_VERSION}/${SPARK_FILE}.tgz
tar -xvzf ${SPARK_FILE}.tgz --one-top-level=spark --strip-components=1
sparkReleasePath="$(pwd)/spark"
```

<a name="build-spark-image"></a>
### Build Spark Docker image
1. Download Spark [#manual](#download-spark)
2. Build the docker image:
   ```shell script
   cd $sparkReleasePath
   sed -i.backup "s/FROM openjdk:[0-9]\+-jdk-slim/FROM openjdk:11-jdk-slim/" kubernetes/dockerfiles/spark/Dockerfile
   ./bin/docker-image-tool.sh -f kubernetes/dockerfiles/spark/Dockerfile build
   ```

## 1. Develop locally, run as process in local docker container
1. Get the source code [#manual](#get-sources)
2. Pull image and start container:
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
1. Build a fat jar for your project: [#manual](#build-jar)
2. Download a Spark release [#manual](#download-spark)
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
### a. With Spark release download
You don't like downloading random things but decided to give it a try:

1. Download Spark [#manual](#download-spark)
2. Build Spark docker image [#manual](#build-spark-image)
3. Build a fat jar [#manual](#build-jar)

Congratulations, your official Spark Docker image is now ready to go.
Now just create a Docker container and  submit your Spark job:

```shell script
docker run -it --rm \
  --mount src=$sbtTargetDir,target=/opt/workspace,type=bind \
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

### b. Without Spark release download
1. Build a fat jar for your project: [#manual](#build-jar)
2. Download Spark and build Spark Docker image with:
   ```shell script
   docker build -t spark:test2 - < Dockerfile2
    
   docker run --rm -it \
     -v /var/run/docker.sock:/var/run/docker.sock \
     -v /usr/bin/docker:/usr/bin/docker spark:test2 \
     ./bin/docker-image-tool.sh -f kubernetes/dockerfiles/spark/Dockerfile build
   ```
3. Submit Spark job:
    ```shell script
    docker run --rm -it \
      -v /var/run/docker.sock:/var/run/docker.sock \
      -v /usr/bin/docker:/usr/bin/docker spark:test2 \
      docker run -it --rm \
        --mount src=$sbtTargetDir,target=/opt/workspace,type=bind \
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

## 5. Submit job to local Kubernetes cluster via Minikube
Nowadays everybody wants to run everything in Kubernetes.
So do we:

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
   
   2. Execute:
      ```shell script
      kubectl apply -f https://raw.githubusercontent.com/mrteutone/ways-of-running-spark/master/pv-volume.yaml
      kubectl apply -f https://raw.githubusercontent.com/mrteutone/ways-of-running-spark/master/pv-claim.yaml
      kubectl get pv
      kubectl get pvc
      ```

6. Download Spark: [#manual](#download-spark)

7. Create a docker image accessible by Minikube:
   1. Follow the instructions printed by `minikube docker-env`
   2. Build a fat jar: [#manual](#build-jar)
   3. Copy the fat jar and log4j conf so that it will be picked up when building the Spark docker image:
   
          cp "$log4jConfFullPath" "$jarFullPath" "$sparkReleasePath/examples/"

   4. Build Spark docker image: [#manual](#build-spark-image)

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

9. To check logs and output find out the Spark driver's pod name with
   
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

## 6. EC2 with CloudFormation
to do

## 7. ECS
to do

## 8. EKS
to do

## 9. EMR
to do

## 10. spark-on-k8s-operator
[to do](https://github.com/GoogleCloudPlatform/spark-on-k8s-operator)
