import sys
import matplotlib.pyplot as plt
import pandas as pd
from matplotlib.ticker import PercentFormatter

values_file = (sys.argv[1] if len(sys.argv) > 1 else "")
root_dir = './'

df=pd.read_csv(root_dir+values_file, sep=',',header=None, names=["quantized_input32_4threads","non_quantized_4threads","quantized_input32_1thread","non_quantized_1thread"])
data1 = pd.to_numeric(df["quantized_input32_4threads"][1:]).to_list()
data2 = pd.to_numeric(df["quantized_input32_1thread"][1:]).to_list()
data3 = pd.to_numeric(df["non_quantized_4threads"][1:]).to_list()
data4 = pd.to_numeric(df["non_quantized_1thread"][1:]).to_list()

'''Frequency Histogram alternative 1'''
#plt.figure(figsize=[10,8])
#bin_values = [0.6, 0.7, 0.8, 0.9, 1.0]
#n, bins, patches = plt.hist([data1[1:],data2[1:]], bins=bin_values,rwidth=1)
#print(n)
#print(bins)

'''Frequency Histogram alternative 2'''
#sb.distplot(pd.to_numeric(df["AhESEAS"][1:]),kde = False, hist=True)
#plt.show()
#sb.distplot(pd.to_numeric(df["FAIR6"][1:]),kde = False)
#plt.show()

'''Frequency Histogram alternative 3'''

# for available styles see: https://matplotlib.org/3.1.0/gallery/style_sheets/style_sheets_reference.html
#plt.style.use('seaborn')
plt.style.use('bmh')

cum=False
den=True

#https://matplotlib.org/gallery/statistics/histogram_multihist.html#sphx-glr-gallery-statistics-histogram-multihist-py
fig, ax = plt.subplots()
n, bins, patches = ax.hist(data1, histtype="stepfilled", bins=60, alpha=1, cumulative=cum, density=den, label='Quantized input32 4threads',color='green')
print(n)
print(bins)
n,bins, patches = ax.hist(data2, histtype="stepfilled", bins=60, alpha=1, cumulative=cum, density=den, label='Quantized input32 1thread',color='lightgreen')
print(n)
print(bins)
n,bins, patches = ax.hist(data3, histtype="stepfilled", bins=60, alpha=1, cumulative=cum, density=den, label='Non quantized 4threads',color='blue')
print(n)
print(bins)
n,bins, patches = ax.hist(data4, histtype="stepfilled", bins=60, alpha=1, cumulative=cum, density=den, label='Non quantized 1thread',color='lightblue')
print(n)
print(bins)
ax.legend(prop={'size': 10},loc='upper left')
ax.set_xlabel('Inference time (milliseconds)')
ax.set_ylabel('Density')
ax.yaxis.set_major_formatter(PercentFormatter(xmax=1))
fig.savefig(root_dir+values_file[0:-4]+".png", format='png')

plt.show()
