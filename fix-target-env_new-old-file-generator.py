import getopt
import sys
import os
import datetime as dt
import pandas as pd
import csv

from colorama import Fore, Back, Style # Colorized output



def print_status(msg):
    print(Back.CYAN + Fore.BLACK + msg + Style.RESET_ALL)

def print_info(msg):
    print(Back.YELLOW + Fore.BLACK + msg + Style.RESET_ALL)

def print_complete(msg):
    print(Back.GREEN + Fore.BLACK + msg + Style.RESET_ALL)

def print_error(msg):
    print(Back.RED + Fore.BLACK + msg + Style.RESET_ALL)


def run_command(cmd):
    print_info(f'||| > Executing command "{cmd}"')
    print("\n" + "=" * 80 + "\n")
    start = dt.datetime.now()
    exit_code = os.system(cmd)
    end = dt.datetime.now()
    print("\n" + "=" * 80 + "\n")
    print_info(f"||| > Command exited with code {exit_code}")
    print_info(f"||| > Time taken : {(end - start)}")
    print("\n" + "=" * 80)

    return exit_code

class AbnormalExitException(Exception):
    pass

def prepare(root, hash_id, identifier, bug_id, loadD4J=True):
    try:
        target_dir = f"{root}/target/{hash_id}_{identifier}-{bug_id}"

        executing_command = f"python3 ./pool/runner_web/commit_collector_web.py -d true -h {hash_id}_{identifier}-{bug_id} -i {identifier}-{bug_id}"
        exit_code = run_command(executing_command)
        if exit_code != 0:
            raise AbnormalExitException

        bfic = pd.read_csv(f'{target_dir}/outputs/commit_collector/BFIC.csv', names = ['Project', 'D4J ID', 'Faulty file path', 'Faulty line', 'FIC_sha', 'BFIC_sha']).values[1]
        if(loadD4J) assert os.system(f"cd {target_dir}; defects4j checkout -p {identifier} -v {bug_id}b -w buggy") == 0, "checkout for buggy project failed"
        #assert os.system(f"cd {target_dir}; defects4j checkout -p {identifier} -v {bug_id}f -w fixed") == 0, "checkout for fixed project failed"

       

        assert os.system(f"cd {target_dir}; mkdir -p outputs; mkdir -p outputs/prepare_pool_source") == 0, "what?"
        # TODO
        # connect ACC in here
        assert os.system(f"cd {root}/LCE\n./run.sh {hash_id} {identifier}-{bug_id}") == 0, 'executing run.sh failed'

        if(loadD4J) assert os.system(f"cp {target_dir}/buggy/{bfic[2]} {root}/LCE/target/targetVector.java") == 0, "copying buggy file failed"
        #assert os.system(f"cp {target_dir}/fixed/{bfic[2]} {target_dir}/outputs/prepare_pool_source/{identifier}_rank-1_new.java") == 0, "copying fixed file failed"

        if(loadD4J) os.system(f"rm -rf {target_dir}/buggy")
        #os.system(f"rm -rf {target_dir}/fixed")
        
        assert os.system(f"cd {target_dir}; touch done") == 0, "You cannot even make dummy file?"

        return '', 0

    except AbnormalExitException as e:
        target_dir = f"{root}/target/{hash_id}_{identifier}-{bug_id}"
        os.system(f"cd {target_dir}; touch failed")
        return e, 1

    except AssertionError as e:
        print(e)
        target_dir = f"{root}/target/{hash_id}_{identifier}-{bug_id}"
        os.system(f"cd {target_dir}; touch failed")
        return e, 1


def main(args):
    whole_start = dt.datetime.now()

    
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
        
        result_str, return_code = prepare(args[1], hash_id, identifier, bug_id)

        if return_code == 0:
            result_str = f"{identifier}-{bug_id}: Pool source prepration finished."
            print_status(result_str)
            successes += 1
        else:
            result_str = f"{identifier}-{bug_id}: Pool source prepration failed. - {result_str}"
            print_error(result_str)
            fails += 1

        os.system(f"echo \"{result_str}\" >> ./target/log_{hash_id}.txt")


    whole_end = dt.datetime.now()
    elapsed_time = (whole_end - whole_start)

    print_complete(f"Batch pool source preparation finished. {successes} succeeded, {fails} failed.")
    os.system(f"echo \"Batch pool source preparation finished. {successes} succeeded, {fails} failed.\" >> ./target/log_{hash_id}.txt")

    print_complete(f"Total Elapsed Time : {elapsed_time}")
    os.system(f"echo \"Total Elapsed Time : {elapsed_time}\" >> ./target/log_{hash_id}.txt")


if __name__ == '__main__':
    main(sys.argv)
