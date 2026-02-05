import { Line } from 'react-chartjs-2';
import {
    Chart as ChartJS,
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    Title,
    Tooltip,
    Legend,
    Filler,
} from 'chart.js';
import { ChartDataPoint } from '../types/reporting';

ChartJS.register(
    CategoryScale,
    LinearScale,
    PointElement,
    LineElement,
    Title,
    Tooltip,
    Legend,
    Filler
);

interface BudgetChartProps {
    title: string;
    data: ChartDataPoint[];
}

function BudgetChart({ title, data }: BudgetChartProps) {
    const labels = data.map(d => d.label);

    const chartData = {
        labels,
        datasets: [
            {
                label: 'Spent',
                data: data.map(d => d.spent),
                borderColor: 'rgb(220, 53, 69)',
                backgroundColor: 'rgba(220, 53, 69, 0.1)',
                tension: 0.1,
            },
            {
                label: 'Allocated',
                data: data.map(d => d.allocated),
                borderColor: 'rgb(40, 167, 69)',
                backgroundColor: 'rgba(40, 167, 69, 0.1)',
                tension: 0.1,
            },
            {
                label: 'Left',
                data: data.map(d => d.left),
                borderColor: 'rgb(255, 165, 0)',
                backgroundColor: 'rgba(255, 165, 0, 0.2)',
                fill: true,
                tension: 0.1,
            },
        ],
    };

    const options = {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: {
                position: 'top' as const,
            },
            title: {
                display: true,
                text: title,
            },
        },
        scales: {
            y: {
                beginAtZero: true,
            },
        },
    };

    return (
        <div style={{ height: '400px', width: '100%' }}>
            <Line data={chartData} options={options} />
        </div>
    );
}

export default BudgetChart;
