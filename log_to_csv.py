import sys
import datetime as dt

def main(args):
    batch_execution_log = None
    with open(args[1], 'r') as infile:
        batch_execution_log = infile.read.splitlines()
    
    for each in batch_execution_log:
        words = each.split()
        exec_time_str = ' '.join(words[-7:-1])
        exec_time = dt.datetime.strptime(exec_time_str, "%a %d %b %Y %I:%M:%S %p")

        patch_idx = words.index('strategy') + 1
        patch_strategy = words[patch_idx].strip('"')
        concretization_idx = words.index('strategy', patch_idx) + 1
        concretization_strategy = words[concretization_idx].strip('"')

        



if __name__ == '__main__':
    main(sys.argv)