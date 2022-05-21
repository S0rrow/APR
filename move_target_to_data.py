import sys
import datetime as dt

import os
import shutil

def main(args):
    bugs_result = dict()
    bugs = list()

    batch_execution_log = None
    with open(args[1], 'r') as infile:
        batch_execution_log = infile.read().splitlines()
    
    for each in batch_execution_log:
        words = each.split()
        exec_time = ' '.join(words[-7:-1])
        exec_time_dt = dt.datetime.strptime(exec_time, "%a %d %b %Y %I:%M:%S %p")
        exec_time_dt_alt = exec_time_dt + dt.timedelta(seconds = 1)

        exec_time_str = exec_time_dt.strftime("%Y%m%d%H%M%S")
        exec_time_str_alt = exec_time_dt_alt.strftime("%Y%m%d%H%M%S")

        patch_idx = words.index('strategy') + 1
        patch_strategy = words[patch_idx].strip('"')
        concretization_idx = words.index('strategy', patch_idx) + 1
        concretization_strategy = words[concretization_idx].strip('"')

        src = f"/home/codemodel/leshen/APR/target"
        dst = f"/data/codemodel/batch_byproducts/{patch_strategy}_and_{concretization_strategy}"

        os.mkdir(f'/data/codemodel/batch_byproducts/{patch_strategy}_and_{concretization_strategy}')

        if os.path.exists(f'{src}/log_batch_{exec_time_str}.txt'):
            os.system(f"mv {src}/batch_{exec_time_str}* {dst}/")
        else:
            os.system(f"mv {src}/batch_{exec_time_str_alt}* {dst}/")

if __name__ == '__main__':
    main(sys.argv)