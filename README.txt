CSC301 A1

Our code is compiled using the ./runme.sh -c command from the rumne.sh script.

This command creates all necessary .class files and adds them to their respective directories in the compiled folder. Any dependencies such as .json files, .jar files, and .py files are also added to the compiled folder.

To run the code, execute the following commands in your linux shell (assuming your current working directory is the a1 folder):
  ./runme.sh -u
  ./runme.sh -p
  ./runme.sh -o
  ./runme.sh -w <workload-file>

If your current working directory is some other folder, the commands can be executed as follows:
  ./path/runme.sh -u
  ./path/runme.sh -p
  ./path/runme.sh -o
  ./path/runme.sh -w <path/workload-file>

The first three commands start the UserService, ProductService, and OrderService servers respectively.
The fourth command starts the Workload Parser with the provided workload from <workload-file>.

Important note: The commands "python3", "java", and "javac" must be recognized by your linux shell before you can execute any of the runme.sh commands.
