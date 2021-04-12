<!-- PROJECT LOGO -->
<br />
<p align="center">
  <a href="https://github.com/bobcorn/ecommerce">
    <img src="https://github.com/bobcorn/ecommerce/blob/master/docs/images/ecommerce_logo.png" alt="Logo" width="600" height="80">
  </a>

<h3 align="center">eCommerce</h3>

  <p align="center">
    A headless e-commerce web application
    <br />
    <a href="https://github.com/bobcorn/ecommerce/blob/master/docs/Report.pdf"><strong>Read the report »</strong></a>
  </p>
</p>

<!-- TABLE OF CONTENTS -->
<details open="open">
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About the project</a>
      <ul>
        <li><a href="#built-with">Built with</a></li>
      </ul>
    </li>
    <li>
      <a href="#getting-started">Getting started</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#installation-windows">Installation (Windows)</a></li>
      </ul>
    </li>
    <li><a href="#usage">Usage</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>

<!-- ABOUT THE PROJECT -->

## About the project

This project defines an implementation proposal for a headless e-commerce web application, developed in the Kotlin
programming language using the Spring Boot framework. The application leverages an architecture based on four
microservices, and features robustness to both logical and physical failures.

Final project for course "Advanced Programming" of the 2nd Level Specializing Master's Programme in *"Artificial Intelligence & Cloud: Hands-On Innovation"*, organized by Polytechnic University of Turin in collaboration with Reply.

### Built with

This application was built leveraging the following languages and frameworks:

* [Kotlin](https://kotlinlang.org/)
* [Spring Boot](https://spring.io/projects/spring-boot)
* [MongoDB](https://www.mongodb.com/)
* [Python](https://www.python.org/)
* [Docker](https://www.docker.com/)

<!-- GETTING STARTED -->

## Getting started

This section provides instructions about how to set up the project locally. To get a local copy up and running please
follow the simple steps described below (please note that the provided commands are intended for the Windows platform).

### Prerequisites

In order to run the installation commands, you need to have Docker installed on your local computer or server. Please
refer to the [Docker documentation](https://docs.docker.com/get-docker/) for installation guidance.

### Installation (Windows)

1. Clone the repository:
   ```bash
   git clone https://github.com/bobcorn/ecommerce.git
   ```
2. Build the `.jar` files for the microservices:
   ```bash
   docker-compose -f .\docker-compose-build.yml up --remove-orphans
   ```
3. Deploy the microservices:
   ```bash
   docker-compose -p ecommerce -f .\docker-compose.yml up -d --remove-orphans
   ```

<!-- USAGE EXAMPLES -->

## Usage

Since the application is headless and does not provide a client-side rendering of the  information,
[Postman](https://www.postman.com/) was used as the reference platform for testing the APIs functionality.

A [Postman collection](https://www.postman.com/collection/) was created in order to provide an easy way of invoking
the set of available APIs (see `eCommerce-requests.json` for more information).

To import the collection into Postman:

1. Click **Import** and select `eCommerce-requests.json` (Postman will automatically recognize the type of file).
    
2. Click **Import** to bring the collection into Postman.

3. After making sure that all services are up and running, you can execute the APIs defined in the imported collection.

<!-- LICENSE -->

## License

Released under the MIT License (see `LICENSE.md` for more information).

<!-- CONTACT -->

## Contact

* Mattia Michelini\
  [s291551@studenti.polito.it](mailto:s291551@studenti.polito.it)


* Manuel Peli\
  [s291485@studenti.polito.it](mailto:s291485@studenti.polito.it)


* Francesco Piemontese\
  [s291491@studenti.polito.it](mailto:s291491@studenti.polito.it)


* Marco Rossini\
  [s291482@studenti.polito.it](mailto:s291482@studenti.polito.it)

Project Link: [https://git-softeng.polito.it/master/group-04/ecommerce](https://github.com/bobcorn/ecommerce)
