
// 젠킨스의 선언형 파이프라인 정의부 시작 (그루비 언어)
pipeline {
    agent any // 어느 젠킨스 서버에서나 실행이 가능
    environment {
        SERVICE_DIRS = "config-service,discovery-service,gateway-service,user-service,ordering-service,product-service"
    }
    stages {
        // 각 작업 단위를 스테이지로 나누어서 작성 가능.
        stage('Pull Codes from Github') { // 스테이지 제목 (맘대로 써도 됨)
            steps {
                checkout scm // 젠킨스와 연결된 소스 컨트롤 매니저(git 등)에서 코드를 가져오는 명령어
            }
        }
        stage('Build Codes by Gradle') {
            steps {
                script {
                   def serviceDirs = env.SERVICE_DIRS.split(",")
                   serviceDirs.each { service ->
                        sh """
                        echo "Building ${service}..."
                        cd ${service}
                        ./gradlew clean build -x test
                        ls -al ./build/libs
                        cd ..
                        """
                   }
                }
            }
        }

    }
}



















