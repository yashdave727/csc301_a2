#include <stdio.h>
#include <pthread.h>
#include <curl/curl.h>
#include <stdlib.h>
#include <unistd.h>
#include <time.h>
#include <string.h>


// Global variables

// URL to send HTTP requests to
char *URL;
// Endpoints
const char *PRODUCT_ENDPOINT = "/product";
const char *USER_ENDPOINT = "/user";
const char *ORDER_ENDPOINT = "/order";
const char *ORDER_HISTORY_ENDPOINT = "/user/purchased";

// Number of threads to create
int NUM_THREADS;
// Number of users and products already in the database
int N;

// Thread arguments struct
typedef struct {
	char *url;
	int thread_id;
	int requests_sent;
} thread_args;



// Function to get a random user/product ID
// Returns a malloc'd string that must be freed
char *get_random_id() {
	// Generate a random user ID between 0 and N
	int user_id = rand() % N;
	// Convert the user ID to a string
	int num_digits = snprintf(NULL, 0, "%d", user_id);
	char *user_id_str = malloc(num_digits + 1);
	sprintf(user_id_str, "%d", user_id);
	return user_id_str;
}

// curl writeback function that does nothing (used to suppress output)
size_t writeback(void *ptr, size_t size, size_t nmemb, void *stream) {
	(void)ptr;
	(void)stream;
	return size * nmemb;
}

// Function to send a GET request to the server
void send_get_request(char *url, const char *endpoint) {
	char *random_id = get_random_id();
	char *full_url = malloc(strlen(url) + 1 + strlen(endpoint) + 1 + strlen(random_id) + 1);
	sprintf(full_url, "%s/%s/%s", url, endpoint, random_id);

	// Send the GET request
	CURL *curl;
	CURLcode res;
	curl = curl_easy_init();
	if (curl) {
		curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, writeback); // Suppress output
		curl_easy_setopt(curl, CURLOPT_URL, full_url);
		curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1);
		res = curl_easy_perform(curl);
		if (res != CURLE_OK) {
			fprintf(stderr, "curl_easy_perform() failed: %s\n", curl_easy_strerror(res));
		}
		// Check the return code
		long response_code;
		curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &response_code);
		if (response_code != 200 && response_code != 307) {
			fprintf(stderr, "GET request failed: %ld\n", response_code);
		}
		curl_easy_cleanup(curl);
	}
	free(random_id); // Free the random ID as it is no longer needed
	free(full_url); // Free the full URL as it is no longer needed
}

const char *json_template = "{\"command\": \"place order\", \"product_id\": %s, \"user_id\": %s, \"quantity\": 1}";

// Function to send a POST request to the server
void send_post_request(char *url, const char *endpoint) {
	char *random_user_id = get_random_id();
	char *random_product_id = get_random_id();
	char *json_body = malloc(strlen(json_template) + strlen(random_product_id) + strlen(random_user_id) + 1);
	sprintf(json_body, json_template, random_product_id, random_user_id);

	char *full_url = malloc(strlen(url) + 1 + strlen(endpoint) + 1);
	sprintf(full_url, "%s/%s", url, endpoint);

	// Send the POST request
	CURL *curl;
	CURLcode res;
	curl = curl_easy_init();
	if (curl) {
		curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, writeback); // Suppress output
		curl_easy_setopt(curl, CURLOPT_URL, full_url);
		curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1);
		curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json_body);
		curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, (long)strlen(json_body));
		res = curl_easy_perform(curl); // Perform the request
		if (res != CURLE_OK) {
			fprintf(stderr, "curl_easy_perform() failed: %s\n", curl_easy_strerror(res));
		}
		// Check the return code
		long response_code;
		curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &response_code);
		if (response_code != 200 && response_code != 307) {
			fprintf(stderr, "POST request failed: %ld\n", response_code);
		}
		curl_easy_cleanup(curl);
	}
	free(random_user_id); // Free the random user ID as it is no longer needed
	free(random_product_id); // Free the random product ID as it is no longer needed
	free(json_body); // Free the JSON body as it is no longer needed
	free(full_url); // Free the full URL as it is no longer needed
}

const char *json_template_update = "{\"command\": \"update\", \"id\": %s}";

// Send a POST request to update the id provided at the given endpoint
void send_post_request_update(char *url, const char *endpoint) {
	char *random_id = get_random_id();
	char *json_body = malloc(strlen(json_template_update) + strlen(random_id) + 1);
	sprintf(json_body, json_template_update, random_id);

	char *full_url = malloc(strlen(url) + 1 + strlen(endpoint) + 1);
	sprintf(full_url, "%s/%s", url, endpoint);

	// Send the POST request
	CURL *curl;
	CURLcode res;
	curl = curl_easy_init();
	if (curl) {
		curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, writeback); // Suppress output
		curl_easy_setopt(curl, CURLOPT_URL, full_url);
		curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1);
		curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json_body);
		curl_easy_setopt(curl, CURLOPT_POSTFIELDSIZE, (long)strlen(json_body));
		res = curl_easy_perform(curl); // Perform the request
		if (res != CURLE_OK) {
			fprintf(stderr, "curl_easy_perform() failed: %s\n", curl_easy_strerror(res));
		}
		// Check the return code
		long response_code;
		curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, &response_code);
		if (response_code != 200) {
			fprintf(stderr, "POST request failed: %ld\n", response_code);
		}
		curl_easy_cleanup(curl);
	}
	free(random_id); // Free the random ID as it is no longer needed
	free(json_body); // Free the JSON body as it is no longer needed
	free(full_url); // Free the full URL as it is no longer needed
}

int running = 1;

// Function to handle HTTP requests in each thread
void *send_requests(void *args) {

	// Get thread arguments
	thread_args *t_args = (thread_args *)args;
	char *url = t_args->url;

	while (running) {
		// Send a random request
		// Randomly choose between getting a product, user, order history
		// Updating a product, user, or placing an order
		switch (rand() % 6) {
			case 0:
				// Get a product
				send_get_request(url, PRODUCT_ENDPOINT);
				break;
			case 1:
				// Get a user
				send_get_request(url, USER_ENDPOINT);
				break;
			case 2:
				// Get order history
				send_get_request(url, ORDER_HISTORY_ENDPOINT);
				break;
			case 3:
				// Update a product
				send_post_request_update(url, PRODUCT_ENDPOINT);
				break;
			case 4:
				// Update a user
				send_post_request_update(url, USER_ENDPOINT);
				break;
			default:
				// Place an order
				send_post_request(url, ORDER_ENDPOINT);
				break;
		}

		// Increment requests_sent
		t_args->requests_sent++;
	}


	pthread_exit(NULL);
}


int main(int argc, char *argv[]) {
	// Read in command line arguments
	// Run the stress test for a specified amount of time in seconds
	// Usage: ./stress_test <URL> <num_threads> <time>

	if (argc != 5) {
		printf("Usage: ./stress_test <URL> <num_threads> <N> <time>\n");
		return 1;
	}

	// Set URL and number of threads
	URL = argv[1];
	NUM_THREADS = atoi(argv[2]);
	N = atoi(argv[3]);
	int time = atoi(argv[4]);

	// curl global initialization
	CURLcode res = curl_global_init(CURL_GLOBAL_DEFAULT);
	if (res != CURLE_OK) {
		fprintf(stderr, "curl_global_init() failed: %s\n", curl_easy_strerror(res));
		return 1;
	}

	// Create threads
	pthread_t threads[NUM_THREADS];
	thread_args args[NUM_THREADS];
	// Time to run the stress test
	clock_t start, end;
	double cpu_time_used;
	start = clock();
	for (int i = 0; i < NUM_THREADS; i++) {
		args[i].url = URL;
		args[i].thread_id = i;
		args[i].requests_sent = 0;
		pthread_create(&threads[i], NULL, send_requests, (void *)&args[i]);
	}
	// Wait for time seconds
	sleep(time);
	running = 0;
	// Join threads
	for (int i = 0; i < NUM_THREADS; i++) {
		pthread_join(threads[i], NULL);
	}
	end = clock();

	// curl global cleanup
	curl_global_cleanup();

	// Calculate the number of requests sent
	int total_requests = 0;
	for (int i = 0; i < NUM_THREADS; i++) {
		total_requests += args[i].requests_sent;
	}

	// Calculate the average requests per second based on the time argument
	double avg_requests_per_second = (double)total_requests / time;
	// Calculate the time taken
	cpu_time_used = ((double)(end - start)) / CLOCKS_PER_SEC;
	// Calculate the average requests per second based on the cpu time used
	double avg_requests_per_second_cpu = (double)total_requests / cpu_time_used;

	// Print results
	printf("Stress test complete\n");
	printf("URL: %s\n", URL);
	printf("Number of threads: %d\n", NUM_THREADS);
	printf("Time: %d seconds\n", time);
	printf("CPU time used: %f\n", cpu_time_used);
	printf("Total requests sent: %d\n", total_requests);
	printf("Average requests per second: %f\n", avg_requests_per_second);
	printf("Requests per second per thread: %f\n", avg_requests_per_second / NUM_THREADS);
	printf("Average requests per second (CPU time used): %f\n", avg_requests_per_second_cpu);
	printf("Requests per second per thread (CPU time used): %f\n", avg_requests_per_second_cpu / NUM_THREADS);

	return 0;
}
