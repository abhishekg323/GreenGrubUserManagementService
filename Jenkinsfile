pipeline {
    agent any

    environment {
        SERVICE_NAME          = 'greengrub-customer-service'
        DOCKER_HUB_USER       = 'subhadipj'
        DOCKER_CREDENTIALS_ID = 'docker_cred'
        K8S_CREDENTIALS_ID    = 'k8s-cluster-config'
        K8S_API_SERVER        = 'https://10.160.0.3:6443'
        SONAR_SERVER_NAME     = 'SonarQube'
    }

    tools {
        maven 'maven_3_9'
        jdk   'JDK17'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'Git_Cred', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    script {
                        env.GITHUB_USERNAME = GH_USER
                        env.GITHUB_TOKEN    = GH_TOKEN
                    }
                    sh 'mvn clean compile -s settings.xml -DskipTests'
                }
            }
        }

        stage('Test') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'Git_Cred', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    script {
                        env.GITHUB_USERNAME = GH_USER
                        env.GITHUB_TOKEN    = GH_TOKEN
                    }
                    sh 'mvn test -s settings.xml -Dspring.profiles.active=local'
                }
            }
        }

        stage('SonarQube Quality Scan') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'Git_Cred', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    script {
                        env.GITHUB_USERNAME = GH_USER
                        env.GITHUB_TOKEN    = GH_TOKEN
                    }
                    withSonarQubeEnv("${SONAR_SERVER_NAME}") {
                        sh """
                            mvn clean verify sonar:sonar -s settings.xml \
                            -Dspring.profiles.active=local \
                            -Dsonar.projectKey=greengrub-customer-service \
                            -Dsonar.projectName="GreenGrub - Customer Service" \
                            -Dsonar.host.url=http://sonar:9000 \
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                        """
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Check Test Coverage') {
            steps {
                script {
                    def coverage = sh(
                        script: "grep -oP 'Total.*?\\K\\d+(?=%)' target/site/jacoco/index.html | tail -1 || echo '0'",
                        returnStdout: true
                    ).trim()
                    echo "Test Coverage: ${coverage}%"
                    if (coverage.toInteger() < 80) {
                        error("Test coverage is ${coverage}%. Minimum required is 80%. Pipeline terminated.")
                    } else {
                        echo "Test coverage (${coverage}%) is above 80%. Proceeding..."
                    }
                }
            }
        }

        stage('Build & Tag Docker Image') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'Git_Cred', usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    sh """
                        docker build \
                            --build-arg GITHUB_USERNAME=\$GH_USER \
                            --build-arg GITHUB_TOKEN=\$GH_TOKEN \
                            -t ${DOCKER_HUB_USER}/${SERVICE_NAME}:${BUILD_NUMBER} .
                    """
                }
            }
        }

        stage('Push to Docker Hub') {
            steps {
                withCredentials([usernamePassword(credentialsId: "${DOCKER_CREDENTIALS_ID}", usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                    sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"
                    sh "docker push ${DOCKER_HUB_USER}/${SERVICE_NAME}:${BUILD_NUMBER}"
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                withKubeConfig([credentialsId: "${K8S_CREDENTIALS_ID}", serverUrl: "${K8S_API_SERVER}"]) {
                    sh "sed -i 's|IMAGE_NAME|${DOCKER_HUB_USER}/${SERVICE_NAME}:${BUILD_NUMBER}|g' k8s.yaml"
                    sh "kubectl apply -f k8s.yaml -n services"
                    sh "kubectl rollout status deployment/customer-service -n services --timeout=780s"
                }
            }
        }
    }

    post {
        always {
            sh "docker rmi ${DOCKER_HUB_USER}/${SERVICE_NAME}:${BUILD_NUMBER} || true"
            cleanWs()
        }
        success {
            echo 'Customer Service pipeline completed successfully.'
        }
        failure {
            echo 'Customer Service pipeline failed.'
        }
    }
}
