import getopt
import sys
import os
import datetime as dt
import pandas as pd
import csv
import time

from colorama import Fore, Back, Style # Colorized output



def print_status(msg):
    print(Back.CYAN + Fore.BLACK + msg + Style.RESET_ALL)

def print_info(msg):
    print(Back.YELLOW + Fore.BLACK + msg + Style.RESET_ALL)

def print_complete(msg):
    print(Back.GREEN + Fore.BLACK + msg + Style.RESET_ALL)

def print_error(msg):
    print(Back.RED + Fore.BLACK + msg + Style.RESET_ALL)

class AbnormalExitException(Exception):
    pass

def launch(root, hash_id, identifier, bug_id):
    try:
        target_dir = f"{root}/target/{hash_id}_{identifier}-{bug_id}"
        output_dir = f"{target_dir}/outputs"

        perfect_info = pd.read_csv(f"{output_dir}/commit_collector/BFIC.csv", names=['Project','D4J ID','Faulty file path','faulty line','FIC sha','BFIC sha']).values[1]
        target_project, target_id, perfect_faulty_path, perfect_faulty_line = perfect_info[0], perfect_info[1], perfect_info[2], perfect_info[3]

        perfect_faulty_class, foo = perfect_faulty_path.split(".")
        perfect_faulty_class = perfect_faulty_class.replace("/", ".")

        project_dir = f"{target_dir}/{target_project}"

        ### for D4J projects
        assert os.system(f"rm -rf {project_dir}; defects4j checkout -p {target_project} -v {target_id}b -w {project_dir}") == 0, "checkout for buggy project failed"
        assert os.system(f"cp {root}/confix/coverages/{target_project.lower()}/{target_project.lower()}{target_id}b/coverage-info.obj {project_dir}") == 0, "failed to bring coverage info"
        os.system(f"cd {project_dir}; {root}/confix/scripts/config.sh {target_project} {target_id} {perfect_faulty_class} {perfect_faulty_line}")
        assert os.system(f"cd {project_dir}; echo \"pool.source={target_dir}/outputs/prepare_pool_source\" >> confix.properties; ") == 0, "what?"

        assert os.system(f"cd {project_dir}; /usr/lib/jvm/java-8-openjdk-amd64/bin/java -Xmx4g -cp ../../../confix/lib/las.jar:../../../confix/lib/confix-ami_torun.jar -Duser.language=en -Duser.timezone=America/Los_Angeles com.github.thwak.confix.main.ConFix > log.txt") == 0, "ConFix Execution failed"

        os.system(f"echo \"end\" >> {target_dir}/status.txt")

        if not os.path.isfile(f"{project_dir}/patches/0/{perfect_faulty_path}"):
            return 'Plausible patch not found', 1

        else:
            git_stream = os.popen(f"cd ~; git diff {project_dir}/{perfect_faulty_path} {project_dir}/patches/0/{perfect_faulty_path}")

            foo = str(git_stream.read()).split('\n')

            os.system(f"echo \"diff --git a a\" > {target_dir}/diff_file.txt")
            os.system(f"echo \"--- {perfect_faulty_path}\" >> {target_dir}/diff_file.txt")
            os.system(f"echo \"+++ {perfect_faulty_path}\" >> {target_dir}/diff_file.txt")
            
            for i in range(4, len(foo)):
                os.system(f"echo \"{foo[i]}\" >> {target_dir}/diff_file.txt")

        return '', 0

    except AbnormalExitException as e:
        return e, 1

    except AssertionError as e:
        print(e)
        return e, 1


def main(args):
    whole_start = dt.datetime.now()
    

    os.system("cd ./confix/ConFix-code; mvn clean package; cp target/confix-0.0.1-SNAPSHOT-jar-with-dependencies.jar /home/codemodel/leshen/APR/confix/lib/confix-ami_torun.jar")

    bugs = []
    successes, fails = 0, 0

    try:
        with open(args[2], 'r') as infile:
            bugs = infile.read().splitlines()
    except Exception as e: # Assume that the script failed to open the file.
        print(e)
        sys.exit(-1)

    hash_id = f'batch_{args[3]}'

    for bug in bugs:
        identifier, bug_id = bug.split('-')
        result_str = ''

        while True:
            if os.path.isfile(f"{args[1]}/target/batch_{args[3]}_{identifier}-{bug_id}/done"):
                result_str, return_code = launch(args[1], hash_id, identifier, bug_id)

                if return_code == 0:
                    result_str = f"{identifier}-{bug_id}: ConFix Execution successfully finished."
                    print_status(result_str)
                    successes += 1
                else:
                    result_str = f"{identifier}-{bug_id}: ConFix failed to generate patch. - {result_str}"
                    print_error(result_str)
                    fails += 1

                os.system(f"echo \"{result_str}\" >> ./target/log_{hash_id}.txt")
                break
            elif os.path.isfile(f"{args[1]}/target/batch_{args[3]}_{identifier}-{bug_id}/failed"):
                result_str = f"{identifier}-{bug_id}: ConFix cannot be executed due to failure of pool source prepration."
                print_error(result_str)
                fails += 1

                os.system(f"echo \"{result_str}\" >> ./target/log_{hash_id}.txt")
                break
            else:
                time.sleep(1)


    whole_end = dt.datetime.now()
    elapsed_time = (whole_end - whole_start)

    print_complete(f"Batch ConFix execution finished. {successes} succeeded, {fails} failed.")
    os.system(f"echo \"Batch ConFix execution finished. {successes} succeeded, {fails} failed.\" >> ./target/log_{hash_id}.txt")

    print_complete(f"Total Elapsed Time : {elapsed_time}")
    os.system(f"echo \"Total Elapsed Time : {elapsed_time}\" >> ./target/log_{hash_id}.txt")


if __name__ == '__main__':
    main(sys.argv)